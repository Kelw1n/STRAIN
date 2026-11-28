import SwiftUI
import UIKit

extension View {
    /// Adds a rightward swipe to STRAIN presentations and their navigation stack.
    func edgeSwipeBack(allowsCenter: Bool = true, _ action: @escaping () -> Void) -> some View {
        modifier(EdgeSwipeBack(navigationOnly: false, allowsCenter: allowsCenter, action: action))
    }

    func navigationSwipeBack() -> some View {
        modifier(EdgeSwipeBack(navigationOnly: true, allowsCenter: true, action: nil))
    }

    func edgeBackSheet<Content: View>(
        isPresented: Binding<Bool>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            content().edgeSwipeBack { isPresented.wrappedValue = false }
        }
    }

    func edgeBackSheet<Item: Identifiable, Content: View>(
        item: Binding<Item?>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping (Item) -> Content
    ) -> some View {
        sheet(item: item, onDismiss: onDismiss) { value in
            content(value).edgeSwipeBack { item.wrappedValue = nil }
        }
    }
}

private struct EdgeSwipeBack: ViewModifier {
    let navigationOnly: Bool
    let allowsCenter: Bool
    let action: (() -> Void)?

    func body(content: Content) -> some View {
        content.background(EdgePanInstaller(navigationOnly: navigationOnly, allowsCenter: allowsCenter, onBack: action))
    }
}

private struct EdgePanInstaller: UIViewRepresentable {
    let navigationOnly: Bool
    let allowsCenter: Bool
    let onBack: (() -> Void)?

    func makeUIView(context: Context) -> EdgePanView {
        let view = EdgePanView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: EdgePanView, context: Context) {
        view.navigationOnly = navigationOnly
        view.allowsCenter = allowsCenter
        view.onBack = onBack
    }

    static func dismantleUIView(_ view: EdgePanView, coordinator: Void) { view.uninstall() }
}

private final class EdgePanView: UIView, UIGestureRecognizerDelegate {
    var onBack: (() -> Void)?
    private weak var owner: UIViewController?
    var navigationOnly = false
    var allowsCenter = true
    private weak var destination: UINavigationController?
    private weak var topAtStart: UIViewController?
    private var fromEdge = false
    private weak var movingView: UIView?
    private var restingTransform = CGAffineTransform.identity
    private var finishing = false
    private lazy var pan: UIPanGestureRecognizer = {
        let recognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        recognizer.maximumNumberOfTouches = 1
        recognizer.delegate = self
        return recognizer
    }()

    override func didMoveToWindow() {
        super.didMoveToWindow()
        uninstall()
        guard window != nil else { return }
        finishing = false
        // The SwiftUI host is in the responder chain after the view is mounted.
        DispatchQueue.main.async { [weak self] in
            guard let self, let window = self.window else { return }
            var responder: UIResponder? = self.next
            while let current = responder {
                if let controller = current as? UIViewController, let host = controller.viewIfLoaded,
                   host.window === window, self.isDescendant(of: host) {
                    self.owner = controller
                    host.addGestureRecognizer(self.pan)
                    return
                }
                responder = current.next
            }
        }
    }

    func uninstall() {
        if !finishing || movingView?.window == nil { movingView?.transform = restingTransform }
        movingView = nil
        pan.view?.removeGestureRecognizer(pan)
        owner = nil
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard let host = pan.view, window != nil, !finishing else { return false }
        fromEdge = touch.location(in: host).x - host.bounds.minX <= 26
        guard fromEdge || allowsCenter else { return false }

        var responder = touch.view as UIResponder?
        var navigation: UINavigationController?
        while let current = responder {
            if let view = current as? UIView, !fromEdge {
                if view is UIControl || view is UITextView { return false }
                if let scroll = view as? UIScrollView,
                   scroll.alwaysBounceHorizontal || scroll.contentSize.width > scroll.bounds.width + 1 {
                    return false
                }
            }
            if let controller = current as? UIViewController {
                if controller.isBeingDismissed || controller.presentedViewController != nil { return false }
                navigation = navigation ?? (controller as? UINavigationController) ?? controller.navigationController
            }
            if current === owner { break }
            responder = current.next
        }

        destination = nil
        topAtStart = nil
        if let navigation, navigation.viewControllers.count > 1 {
            // Leave the left edge to UIKit's interactive navigation transition.
            if fromEdge, navigation.interactivePopGestureRecognizer?.isEnabled == true { return false }
            guard navigation.transitionCoordinator == nil else { return false }
            destination = navigation
            topAtStart = navigation.topViewController
            return true
        }
        return !navigationOnly
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let velocity = pan.velocity(in: pan.view)
        return velocity.x > 0 && velocity.x > abs(velocity.y) * 1.5
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        if let scroll = otherGestureRecognizer.view as? UIScrollView {
            return otherGestureRecognizer === scroll.panGestureRecognizer
        }
        return false
    }

    private func presentedSurface() -> UIView? {
        var controller = owner
        while let current = controller {
            if let presentation = current.presentationController,
               presentation.presentedViewController === current, let surface = presentation.presentedView {
                return surface
            }
            controller = current.parent
        }
        return pan.view
    }

    private func moveSheet(by distance: CGFloat) {
        movingView?.transform = restingTransform.concatenating(
            CGAffineTransform(translationX: max(0, distance), y: 0)
        )
    }

    private func resetSheet() {
        guard let surface = movingView else { return }
        movingView = nil
        let original = restingTransform
        UIView.animate(withDuration: 0.25, delay: 0, options: [.beginFromCurrentState, .allowUserInteraction, .curveEaseOut]) {
            surface.transform = original
        }
    }

    private func finishSheet() {
        guard let onBack else { resetSheet(); return }
        finishing = true
        let closeWithoutVerticalAnimation = {
            UIView.performWithoutAnimation {
                var transaction = Transaction(animation: nil)
                transaction.disablesAnimations = true
                withTransaction(transaction, onBack)
            }
        }
        guard let surface = movingView else {
            closeWithoutVerticalAnimation()
            return
        }
        let original = restingTransform
        let travel = surface.window?.bounds.width ?? surface.bounds.width
        UIView.animate(withDuration: 0.22, delay: 0, options: [.beginFromCurrentState, .curveEaseOut]) {
            surface.transform = original.concatenating(CGAffineTransform(translationX: travel, y: 0))
        } completion: { [weak self] finished in
            guard let self, self.window != nil else { return }
            if finished {
                closeWithoutVerticalAnimation()
            } else {
                self.finishing = false
                self.resetSheet()
            }
        }
    }

    @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
        let distance = recognizer.translation(in: window)
        switch recognizer.state {
        case .began, .changed:
            guard destination == nil, !navigationOnly, !UIAccessibility.isReduceMotionEnabled else { return }
            if recognizer.state == .began {
                movingView = presentedSurface()
                restingTransform = movingView?.transform ?? .identity
            }
            moveSheet(by: distance.x)
        case .ended:
            let speed = recognizer.velocity(in: window)
            let minimum: CGFloat = fromEdge ? 65 : 90
            guard distance.x > abs(distance.y) * 1.5,
                  distance.x >= minimum || (distance.x >= minimum / 2 && speed.x >= 500) else {
                resetSheet()
                return
            }
            if let destination {
                guard destination.topViewController === topAtStart, destination.transitionCoordinator == nil else { return }
                destination.popViewController(animated: true)
            } else if !navigationOnly {
                finishSheet()
            }
        case .cancelled, .failed:
            resetSheet()
        default:
            break
        }
    }
}
