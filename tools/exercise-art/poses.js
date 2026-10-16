const { build, drawFigure, P, step } = require('./figure');

// Таз → плечо: torso. Руки [плечо→локоть, локоть→кисть], ноги [таз→колено, колено→лодыжка, лодыжка→носок].
const fig = (spec, hl) => { const f = build(spec); return { f, s: drawFigure(f, hl) }; };
const scaled = (content, k, cx, cy) => `<g transform="translate(${cx} ${cy}) scale(${k}) translate(${-cx} ${-cy})">${content}</g>`;
const seated = { legs: [[-4, -94, 0], [0, -92, 0]] };

const poses = {
  squat: () => {
    const { f, s } = fig({ hip: [42, 66], torso: 55, headDir: 75, arms: [[-165, 72], [-160, 74]], legs: [[-10, -112, 0], [-6, -108, 0]] }, ['thighs', 'glutes']);
    return P.floor(88) + P.shadow(50) + P.plate({ x: f.shoulder.x - 3, y: f.shoulder.y + 1.5 }) + s;
  },
  front_squat: () => {
    const { f, s } = fig({ hip: [44, 66], torso: 72, headDir: 84, arms: [[-20, 150], [-15, 150]], legs: [[-12, -110, 0], [-8, -106, 0]] }, ['thighs', 'glutes']);
    return P.floor(88) + P.shadow(50) + P.plate({ x: f.shoulder.x + 2, y: f.shoulder.y + 1 }) + s;
  },
  goblet: () => {
    const { f, s } = fig({ hip: [44, 66], torso: 74, headDir: 84, arms: [[-60, 110], [-55, 112]], legs: [[-12, -110, 0], [-8, -106, 0]] }, ['thighs', 'glutes']);
    return P.floor(88) + P.shadow(50) + s + P.kettlebell({ x: f.armN.h.x + 1, y: f.armN.h.y - 2 });
  },
  deadlift: () => {
    const { f, s } = fig({ hip: [36, 60], torso: 30, headDir: 45, arms: [[-88, -90]], legs: [[-26, -98, 0], [-22, -96, 0]] }, ['back', 'lowback', 'thighs', 'glutes']);
    return P.floor(88) + P.shadow(52) + P.plate({ x: f.armN.h.x, y: 77.5 }) + s;
  },
  rdl: () => {
    const { f, s } = fig({ hip: [34, 50], torso: 10, headDir: 20, arms: [[-80, -88]], legs: [[-78, -96, 0], [-74, -94, 0]] }, ['thighs', 'glutes', 'lowback']);
    return P.floor(88) + P.shadow(46) + P.plate({ x: f.armN.h.x, y: f.armN.h.y + 2 }) + s;
  },
  bench: () => {
    const { f, s } = fig({ hip: [64, 63], torso: 180, headDir: 180, face: -1, arms: [[98, 90], [102, 92]], legs: [[-30, -100, 0], [-36, -96, 0]] }, ['chest', 'arms']);
    return P.floor(88) + P.shadow(50, 48) + P.bench(26, 72, 66) + P.plate({ x: f.armN.h.x, y: f.armN.h.y }) + s;
  },
  incline: () => {
    const { f, s } = fig({ hip: [62, 64], torso: 150, headDir: 150, face: -1, arms: [[96, 88], [100, 90]], legs: [[-20, -100, 0], [-26, -96, 0]] }, ['chest', 'delts']);
    return P.floor(88) + P.shadow(48, 46) + P.incline(66, 68, 34, 150) + P.box(58, 67, 14, 4) + P.plate({ x: f.armN.h.x, y: f.armN.h.y }) + s;
  },
  ohp: () => {
    const { f, s } = fig({ hip: [48, 52], torso: 92, headDir: 98, arms: [[70, 96], [75, 98]], legs: [[-92, -90, 0], [-86, -90, 0]] }, ['delts', 'arms']);
    return P.floor(88) + P.shadow(50, 22) + scaled(P.plate({ x: f.armN.h.x, y: f.armN.h.y }, 9) + s, 0.8, 50, 88);
  },
  seated_press: () => {
    const { f, s } = fig({ hip: [44, 62], torso: 92, headDir: 96, arms: [[30, 95], [40, 97]], ...seated }, ['delts', 'arms']);
    return P.floor(88) + P.shadow(50, 30) + P.box(30, 64, 26, 24) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
  pullup: () => {
    const { f, s } = fig({ hip: [50, 62], torso: 90, headDir: 92, arms: [[125, 72], [118, 76]], legs: [[-80, -128, -160], [-86, -124, -156]] }, ['back', 'arms']);
    return scaled(P.bar(16, 84, f.armN.h.y) + s, 0.86, 50, 8);
  },
  pulldown: () => {
    const { f, s } = fig({ hip: [42, 64], torso: 96, headDir: 98, arms: [[105, 80], [110, 82]], ...seated }, ['back', 'arms']);
    return P.floor(88) + P.shadow(48, 30) + P.tower(76, 8) + P.cable({ x: 76, y: 11 }, { x: f.armN.h.x, y: f.armN.h.y })
      + P.bar(f.armN.h.x - 12, f.armN.h.x + 8, f.armN.h.y) + P.box(30, 66, 24, 22) + s;
  },
  row: () => {
    const { f, s } = fig({ hip: [36, 58], torso: 28, headDir: 36, arms: [[-120, -70], [-115, -68]], legs: [[-60, -100, 0], [-56, -98, 0]] }, ['back', 'arms']);
    return P.floor(88) + P.shadow(48) + P.plate({ x: f.armN.h.x + 1, y: f.armN.h.y + 2 }) + s;
  },
  cable_row: () => {
    const { f, s } = fig({ hip: [34, 76], torso: 96, headDir: 98, arms: [[-150, -10], [-145, -8]], legs: [[-6, -8, 80], [-4, -6, 80]] }, ['back', 'arms']);
    return P.floor(88) + P.shadow(50, 44) + P.tower(86, 30) + P.cable({ x: 86, y: f.armN.h.y }, { x: f.armN.h.x, y: f.armN.h.y }) + P.box(24, 78, 22, 10) + s;
  },
  dips: () => {
    const { f, s } = fig({ hip: [48, 60], torso: 84, headDir: 88, arms: [[-150, -78], [-145, -80]], legs: [[-100, -140, -160], [-96, -136, -156]] }, ['chest', 'arms']);
    return scaled(P.bar(f.armN.h.x - 14, f.armN.h.x + 14, f.armN.h.y + 1) + s, 0.86, 50, 12);
  },
  pushup: () => {
    const { f, s } = fig({ hip: [40, 72], torso: 12, headDir: 14, arms: [[-60, -100], [-56, -98]], legs: [[-176, -178, -110], [-174, -176, -110]] }, ['chest', 'arms']);
    return P.floor(88) + P.shadow(50, 60) + s;
  },
  lateral_raise: () => {
    const { f, s } = fig({ hip: [48, 52], torso: 90, headDir: 92, arms: [[2, 0], [-6, -4]], legs: [[-92, -90, 0], [-87, -90, 0]] }, ['delts']);
    return P.floor(88) + P.shadow(50, 26) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
  curl: () => {
    const { f, s } = fig({ hip: [48, 53], torso: 90, headDir: 92, arms: [[-96, 62], [-88, -84]], legs: [[-92, -90, 0], [-87, -90, 0]] }, ['arms']);
    return P.floor(88) + P.shadow(50, 26) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
  pushdown: () => {
    const { f, s } = fig({ hip: [44, 53], torso: 96, headDir: 98, arms: [[-82, -70], [-86, -72]], legs: [[-94, -90, 0], [-86, -90, 0]] }, ['arms']);
    return P.floor(88) + P.shadow(48, 26) + P.tower(72, 8) + P.cable({ x: 72, y: 11 }, { x: f.armN.h.x, y: f.armN.h.y }) + s;
  },
  leg_curl: () => {
    const { f, s } = fig({ hip: [52, 60], torso: 180, headDir: 172, face: 1, arms: [[-100, -170], [-96, -172]], legs: [[2, 100, 100], [-2, 96, 96]] }, ['thighs']);
    return P.floor(88) + P.shadow(50, 50) + P.bench(20, 78, 64) + s;
  },
  leg_extension: () => {
    const { f, s } = fig({ hip: [40, 62], torso: 98, headDir: 98, arms: [[-80, -20], [-84, -22]], legs: [[-4, 4, 80], [0, 6, 80]] }, ['thighs']);
    return P.floor(88) + P.shadow(48, 34) + P.box(26, 64, 24, 24) + P.box(22, 36, 5, 30) + s;
  },
  leg_press: () => {
    const { f, s } = fig({ hip: [36, 72], torso: 138, headDir: 128, face: -1, arms: [[-60, -20], [-56, -18]], legs: [[44, 18, 100], [48, 22, 104]] }, ['thighs', 'glutes']);
    const p = f.legN.a;
    return P.floor(88) + P.shadow(48, 50) + P.incline(38, 76, 26, 138) + P.box(30, 74, 14, 14)
      + `<g transform="rotate(-30 ${p.x + 3} ${p.y})"><rect x="${p.x + 1.5}" y="${p.y - 11}" width="4" height="22" rx="1.5" fill="#2E2E2E"/></g>` + s;
  },
  calf: () => {
    const { f, s } = fig({ hip: [48, 47], torso: 90, headDir: 92, arms: [[-92, -90], [-86, -90]], legs: [[-90, -90, -40], [-86, -90, -40]] }, ['calves']);
    return P.floor(88) + P.box(42, 82, 20, 6) + P.shadow(52, 26) + s;
  },
  plank: () => {
    const { f, s } = fig({ hip: [40, 74], torso: 6, headDir: 10, arms: [[-90, 0], [-88, 2]], legs: [[-178, -178, -110], [-176, -176, -110]] }, ['abs']);
    return P.floor(88) + P.shadow(48, 64) + s;
  },
  crunch: () => {
    const { f, s } = fig({ hip: [52, 82], torso: 150, headDir: 140, face: -1, arms: [[130, -40], [126, -44]], legs: [[50, -60, 0], [54, -56, 0]] }, ['abs']);
    return P.floor(88) + P.shadow(48, 50) + s;
  },
  hanging_raise: () => {
    const { f, s } = fig({ hip: [50, 60], torso: 92, headDir: 92, arms: [[92, 90], [88, 90]], legs: [[-4, -6, 20], [0, -2, 20]] }, ['abs']);
    return P.bar(20, 80, f.armN.h.y) + s;
  },
  hyperext: () => {
    const { f, s } = fig({ hip: [44, 58], torso: 4, headDir: 8, arms: [[-120, 160], [-116, 162]], legs: [[-150, -150, -120], [-146, -148, -116]] }, ['lowback', 'glutes']);
    return P.floor(88) + P.shadow(42, 44) + P.box(38, 62, 16, 4) + P.incline(30, 86, 22, 30) + s;
  },
  lunge: () => {
    const { f, s } = fig({ hip: [46, 62], torso: 90, headDir: 92, arms: [[-92, -90], [-88, -90]], legs: [[-10, -92, 0], [-118, -170, -110]] }, ['thighs', 'glutes']);
    return P.floor(88) + P.shadow(50, 44) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
  hip_thrust: () => {
    const { f, s } = fig({ hip: [52, 64], torso: 170, headDir: 160, face: -1, arms: [[-120, -60], [-116, -58]], legs: [[0, -88, 0], [4, -86, 0]] }, ['glutes']);
    return P.floor(88) + P.shadow(54, 50) + P.box(18, 66, 16, 22) + P.plate({ x: f.hip.x, y: f.hip.y - 9 }, 8) + s;
  },
  shrug: () => {
    const { f, s } = fig({ hip: [48, 52], torso: 90, headDir: 92, arms: [[-92, -90], [-86, -90]], legs: [[-92, -90, 0], [-87, -90, 0]] }, ['traps', 'forearms']);
    return P.floor(88) + P.shadow(50, 26) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
  generic: () => {
    const { f, s } = fig({ hip: [48, 52], torso: 90, headDir: 92, arms: [[-100, -95], [-82, -86]], legs: [[-94, -90, 0], [-84, -90, 0]] }, []);
    return P.floor(88) + P.shadow(50, 26) + P.dumbbell(f.armF.h) + s + P.dumbbell(f.armN.h);
  },
};
module.exports = poses;
