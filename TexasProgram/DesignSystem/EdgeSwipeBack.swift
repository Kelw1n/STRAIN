import SwiftUI
import UIKit

extension View {
    /// Adds the left-edge gesture to dismissible STRAIN presentations. A pushed
    /// destination inside the presentation keeps UIKit's interactive pop.
    func edgeSwipeBack(_ action: (() -> Void)? = nil) -> some View {
        modifier(EdgeSwipeBack(action: action))
    }

    func edgeBackSheet<Content: View>(
        isPresented: Binding<Bool>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) { content().edgeSwipeBack() }
    }

    func edgeBackSheet<Item: Identifiable, Content: View>(
        item: Binding<Item?>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping (Item) -> Content
    ) -> some View {
        sheet(item: item, onDismiss: onDismiss) { content($0).edgeSwipeBack() }
    }
}

private struct EdgeSwipeBack: ViewModifier {
    @Environment(\.dismiss) private var dismiss
    let action: (() -> Void)?

    func body(content: Content) -> some View {
        content.background(EdgePanInstaller { if let action { action() } else { dismiss() } })
    }
}

private struct EdgePanInstaller: UIViewRepresentable {
    let onBack: () -> Void

    func makeUIView(context: Context) -> EdgePanView {
        let view = EdgePanView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: EdgePanView, context: Context) { view.onBack = onBack }

    static func dismantleUIView(_ view: EdgePanView, coordinator: Void) { view.uninstall() }
}

private final class EdgePanView: UIView, UIGestureRecognizerDelegate {
    var onBack: (() -> Void)?
    private weak var owner: UIViewController?
    private lazy var pan: UIScreenEdgePanGestureRecognizer = {
        let recognizer = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        recognizer.edges = .left
        recognizer.delegate = self
        return recognizer
    }()

    override func didMoveToWindow() {
        super.didMoveToWindow()
        uninstall()
        guard window != nil else { return }
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
        pan.view?.removeGestureRecognizer(pan)
        owner = nil
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard window != nil else { return false }
        var responder = touch.view as UIResponder?
        while let current = responder {
            if let controller = current as? UIViewController {
                // A child may be presented in its own host within the outer host.
                if controller.isBeingDismissed || controller.presentedViewController != nil { return false }
                let navigation = (controller as? UINavigationController) ?? controller.navigationController
                if let navigation, navigation.viewControllers.count > 1 { return false }
            }
            if current === owner { break }
            responder = current.next
        }
        return true
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let velocity = pan.velocity(in: pan.view)
        return velocity.x > 0 && velocity.x > abs(velocity.y)
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

    @objc private func handlePan(_ recognizer: UIScreenEdgePanGestureRecognizer) {
        guard recognizer.state == .ended else { return }
        let distance = recognizer.translation(in: recognizer.view)
        let speed = recognizer.velocity(in: recognizer.view)
        if distance.x > abs(distance.y) && (distance.x >= 72 || (distance.x >= 32 && speed.x >= 450)) {
            onBack?()
        }
    }
}
