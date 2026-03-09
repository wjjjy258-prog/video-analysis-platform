<template>
  <div class="three-wrap">
    <div v-if="safeItems.length" class="three-toolbar">
      <p class="toolbar-help">拖拽旋转，滚轮缩放，右键平移</p>
      <div class="toolbar-actions">
        <button type="button" @click="resetView">重置视角</button>
        <button type="button" @click="autoRotate = !autoRotate">
          {{ autoRotate ? "关闭自动旋转" : "开启自动旋转" }}
        </button>
      </div>
    </div>

    <div class="scene-stage">
      <div ref="mountRef" class="three-scene"></div>
      <div v-if="safeItems.length" class="label-overlay">
        <div
          v-for="label in screenLabels"
          :key="label.id"
          class="screen-label"
          :class="label.kind"
          :style="{
            left: `${label.x}px`,
            top: `${label.y}px`,
            opacity: label.visible ? 1 : 0
          }"
        >
          {{ label.text }}
        </div>
      </div>
    </div>

    <p v-if="!safeItems.length" class="three-hint">暂无数据</p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { SMAAPass } from "three/examples/jsm/postprocessing/SMAAPass.js";

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  }
});

const safeItems = computed(() =>
  (props.items ?? [])
    .filter((item) => Number(item?.value ?? 0) >= 0)
    .slice(0, 12)
);

const mountRef = ref(null);
const autoRotate = ref(false);
const screenLabels = ref([]);

const SCENE_BASE_Y = -1.18;
const SCENE_MAX_H = 3.2;
const BAR_WIDTH = 0.55;
const BAR_GAP = 0.24;
const CAMERA_DEFAULT = new THREE.Vector3(0, 2.45, 8.4);
const CAMERA_TARGET = new THREE.Vector3(0, -0.18, 0);

let renderer = null;
let scene = null;
let camera = null;
let controls = null;
let barGroup = null;
let guideGroup = null;
let composer = null;
let smaaPass = null;
let envTexture = null;
let animationId = 0;
let labelAnchors = [];

const toShortLabel = (text) => {
  const value = String(text ?? "").trim();
  if (!value) return "-";
  return value.length <= 8 ? value : `${value.slice(0, 8)}…`;
};

const formatNumber = (value) => Number(value ?? 0).toLocaleString("zh-CN");

const rankColor = (idx, total) => {
  const start = new THREE.Color(0x1f93ff);
  const end = new THREE.Color(0xf59f0b);
  if (total <= 1) return start.getHex();
  return start.lerp(end, idx / (total - 1)).getHex();
};

const clearGroup = (group) => {
  if (!group) return;
  for (let i = group.children.length - 1; i >= 0; i -= 1) {
    const node = group.children[i];
    node.traverse((child) => {
      if (child.geometry) child.geometry.dispose();
      if (child.material) {
        if (Array.isArray(child.material)) {
          child.material.forEach((m) => m.dispose());
        } else {
          child.material.dispose();
        }
      }
    });
    group.remove(node);
  }
};

const computeBarHeight = (value, maxValue) => {
  if (maxValue <= 0) return 0.05;
  return Math.max(0.05, (Number(value ?? 0) / maxValue) * SCENE_MAX_H);
};

const buildGuides = (leftX, rightX, maxValue) => {
  clearGroup(guideGroup);
  const levels = 5;

  for (let i = 0; i < levels; i += 1) {
    const ratio = i / (levels - 1);
    const y = SCENE_BASE_Y + ratio * SCENE_MAX_H;

    const points = [
      new THREE.Vector3(leftX, y, -0.35),
      new THREE.Vector3(rightX, y, -0.35)
    ];
    const geom = new THREE.BufferGeometry().setFromPoints(points);
    const mat = new THREE.LineBasicMaterial({
      color: i === 0 ? 0x95bce7 : 0xbdd5f0,
      transparent: true,
      opacity: i === 0 ? 0.9 : 0.7
    });
    const line = new THREE.Line(geom, mat);
    guideGroup.add(line);

    labelAnchors.push({
      id: `tick-${i}`,
      kind: "tick",
      text: formatNumber(Math.round(maxValue * ratio)),
      position: new THREE.Vector3(leftX - 0.25, y, 0.14)
    });
  }
};

const buildBars = () => {
  if (!barGroup || !guideGroup) return;
  clearGroup(barGroup);
  clearGroup(guideGroup);
  labelAnchors = [];

  const data = safeItems.value;
  if (!data.length) {
    screenLabels.value = [];
    return;
  }

  const maxValue = Math.max(...data.map((item) => Number(item.value ?? 0)), 1);
  const count = data.length;
  const totalW = count * BAR_WIDTH + (count - 1) * BAR_GAP;
  const startX = -totalW / 2 + BAR_WIDTH / 2;
  const leftX = startX - BAR_WIDTH * 0.6;
  const rightX = startX + (count - 1) * (BAR_WIDTH + BAR_GAP) + BAR_WIDTH * 0.6;

  buildGuides(leftX, rightX, maxValue);

  data.forEach((item, idx) => {
    const value = Number(item.value ?? 0);
    const height = computeBarHeight(value, maxValue);

    const geom = new RoundedBoxGeometry(BAR_WIDTH, height, BAR_WIDTH, 10, 0.09);
    const color = rankColor(idx, count);
    const mat = new THREE.MeshPhysicalMaterial({
      color,
      roughness: 0.24,
      metalness: 0.22,
      clearcoat: 1,
      clearcoatRoughness: 0.2,
      envMapIntensity: 1.2,
      emissive: color,
      emissiveIntensity: 0.045
    });
    const mesh = new THREE.Mesh(geom, mat);
    const x = startX + idx * (BAR_WIDTH + BAR_GAP);
    mesh.position.set(x, SCENE_BASE_Y + height / 2, 0);
    mesh.castShadow = true;
    mesh.receiveShadow = false;

    barGroup.add(mesh);

    labelAnchors.push({
      id: `name-${idx}`,
      kind: "name",
      text: toShortLabel(item.label ?? item.name),
      position: new THREE.Vector3(x, SCENE_BASE_Y - 0.1, 0.34)
    });

    labelAnchors.push({
      id: `value-${idx}`,
      kind: "value",
      text: formatNumber(value),
      position: new THREE.Vector3(x, SCENE_BASE_Y + height + 0.12, 0.04)
    });
  });
};

const updateLabels = () => {
  if (!mountRef.value || !camera || !labelAnchors.length) {
    screenLabels.value = [];
    return;
  }
  const width = mountRef.value.clientWidth || 1;
  const height = mountRef.value.clientHeight || 1;
  const result = [];

  for (let i = 0; i < labelAnchors.length; i += 1) {
    const anchor = labelAnchors[i];
    const projected = anchor.position.clone().project(camera);
    const visible = projected.z > -1 && projected.z < 1;

    result.push({
      id: anchor.id,
      kind: anchor.kind,
      text: anchor.text,
      x: (projected.x * 0.5 + 0.5) * width,
      y: (-projected.y * 0.5 + 0.5) * height,
      visible
    });
  }
  screenLabels.value = result;
};

const resetView = () => {
  if (!camera || !controls) return;
  camera.position.copy(CAMERA_DEFAULT);
  controls.target.copy(CAMERA_TARGET);
  controls.update();
  updateLabels();
};

const resize = () => {
  if (!mountRef.value || !renderer || !camera) return;
  const width = mountRef.value.clientWidth || 800;
  const height = mountRef.value.clientHeight || 380;
  const pxRatio = Math.min((window.devicePixelRatio || 1) * 1.35, 3);
  renderer.setPixelRatio(pxRatio);
  renderer.setSize(width, height, true);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  if (composer) {
    composer.setSize(width, height);
  }
  if (smaaPass) {
    smaaPass.setSize(width * pxRatio, height * pxRatio);
  }
  updateLabels();
};

const renderLoop = () => {
  animationId = requestAnimationFrame(renderLoop);
  if (controls) controls.update();
  updateLabels();
  if (composer) {
    composer.render();
  } else if (renderer && scene && camera) {
    renderer.render(scene, camera);
  }
};

onMounted(() => {
  const el = mountRef.value;
  if (!el) return;
  const width = el.clientWidth || 800;
  const height = el.clientHeight || 380;

  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0xe7f2ff, 11, 23);

  camera = new THREE.PerspectiveCamera(44, width / height, 0.1, 100);
  camera.position.copy(CAMERA_DEFAULT);
  camera.lookAt(CAMERA_TARGET);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  const pxRatio = Math.min((window.devicePixelRatio || 1) * 1.35, 3);
  renderer.setPixelRatio(pxRatio);
  renderer.setSize(width, height, true);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.12;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  el.appendChild(renderer.domElement);

  const pmrem = new THREE.PMREMGenerator(renderer);
  const envScene = new RoomEnvironment();
  envTexture = pmrem.fromScene(envScene, 0.04).texture;
  scene.environment = envTexture;
  envScene.dispose();
  pmrem.dispose();

  composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  smaaPass = new SMAAPass(width * pxRatio, height * pxRatio);
  composer.addPass(smaaPass);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.target.copy(CAMERA_TARGET);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.enablePan = true;
  controls.minDistance = 4.5;
  controls.maxDistance = 14;
  controls.minPolarAngle = 0.26;
  controls.maxPolarAngle = Math.PI / 2.06;
  controls.autoRotate = autoRotate.value;
  controls.autoRotateSpeed = 0.78;

  const hemi = new THREE.HemisphereLight(0xe9f4ff, 0xdbe9fb, 0.7);
  scene.add(hemi);

  const ambient = new THREE.AmbientLight(0xffffff, 0.22);
  scene.add(ambient);

  const keyLight = new THREE.DirectionalLight(0xffffff, 1.24);
  keyLight.position.set(5, 8, 4);
  keyLight.castShadow = true;
  keyLight.shadow.mapSize.width = 2048;
  keyLight.shadow.mapSize.height = 2048;
  keyLight.shadow.camera.near = 1;
  keyLight.shadow.camera.far = 24;
  keyLight.shadow.camera.left = -8;
  keyLight.shadow.camera.right = 8;
  keyLight.shadow.camera.top = 8;
  keyLight.shadow.camera.bottom = -8;
  keyLight.shadow.radius = 4;
  keyLight.shadow.blurSamples = 8;
  keyLight.shadow.bias = -0.00016;
  keyLight.shadow.normalBias = 0.03;
  scene.add(keyLight);

  const rimLight = new THREE.DirectionalLight(0x9ed2ff, 0.58);
  rimLight.position.set(-6, 4, -5);
  scene.add(rimLight);

  const floorGeom = new THREE.CylinderGeometry(4.2, 4.2, 0.08, 80);
  const floorMat = new THREE.MeshPhysicalMaterial({
    color: 0xe9f4ff,
    roughness: 0.3,
    metalness: 0.22,
    clearcoat: 0.8,
    clearcoatRoughness: 0.32,
    envMapIntensity: 0.94
  });
  const floor = new THREE.Mesh(floorGeom, floorMat);
  floor.position.y = SCENE_BASE_Y - 0.05;
  floor.receiveShadow = true;
  scene.add(floor);

  const ringGeom = new THREE.TorusGeometry(3.95, 0.045, 12, 120);
  const ringMat = new THREE.MeshPhysicalMaterial({
    color: 0x9fc9f2,
    transparent: true,
    opacity: 0.88,
    roughness: 0.24,
    metalness: 0.35,
    clearcoat: 1,
    clearcoatRoughness: 0.28
  });
  const ring = new THREE.Mesh(ringGeom, ringMat);
  ring.position.y = SCENE_BASE_Y + 0.01;
  ring.rotation.x = Math.PI / 2;
  ring.receiveShadow = true;
  scene.add(ring);

  barGroup = new THREE.Group();
  scene.add(barGroup);
  guideGroup = new THREE.Group();
  scene.add(guideGroup);

  buildBars();
  window.addEventListener("resize", resize);
  renderLoop();
});

watch(
  () => props.items,
  () => {
    buildBars();
    updateLabels();
  },
  { deep: true }
);

watch(autoRotate, (enabled) => {
  if (controls) controls.autoRotate = enabled;
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resize);
  if (animationId) cancelAnimationFrame(animationId);

  clearGroup(barGroup);
  clearGroup(guideGroup);
  labelAnchors = [];
  screenLabels.value = [];

  if (controls) controls.dispose();

  if (scene) {
    scene.traverse((obj) => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) {
        if (Array.isArray(obj.material)) {
          obj.material.forEach((m) => m.dispose());
        } else {
          obj.material.dispose();
        }
      }
    });
  }

  if (envTexture) {
    envTexture.dispose();
    envTexture = null;
  }

  if (composer) {
    composer.dispose();
    composer = null;
  }
  smaaPass = null;

  if (renderer) {
    renderer.dispose();
    if (mountRef.value && renderer.domElement && mountRef.value.contains(renderer.domElement)) {
      mountRef.value.removeChild(renderer.domElement);
    }
  }

  renderer = null;
  scene = null;
  camera = null;
  controls = null;
  barGroup = null;
  guideGroup = null;
});
</script>

<style scoped>
.three-wrap {
  position: relative;
}

.three-toolbar {
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-help {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.toolbar-actions button {
  border: 1px solid #cddcf2;
  border-radius: 999px;
  background: #f8fbff;
  color: #334155;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.toolbar-actions button:hover {
  border-color: #94c7ff;
  color: #175ca9;
}

.scene-stage {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), 0 10px 26px rgba(25, 78, 143, 0.12);
}

.three-scene {
  width: 100%;
  min-height: 400px;
  border: 1px solid #d7e6f8;
  background:
    radial-gradient(circle at 18% 22%, rgba(42, 163, 255, 0.3), rgba(255, 255, 255, 0.7)),
    radial-gradient(circle at 82% 14%, rgba(159, 202, 244, 0.24), transparent 46%),
    linear-gradient(160deg, #f7fbff, #edf5ff);
}

.label-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.screen-label {
  position: absolute;
  transition: opacity 0.12s ease;
  will-change: transform, left, top, opacity;
}

.screen-label.value {
  transform: translate(-50%, -110%);
  font-size: 12px;
  color: #0f172a;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #cfdff4;
  border-radius: 8px;
  padding: 1px 6px;
}

.screen-label.name {
  transform: translate(-50%, 8px);
  font-size: 11px;
  color: #38506d;
  background: rgba(247, 251, 255, 0.86);
  border: 1px solid #d7e4f5;
  border-radius: 6px;
  padding: 1px 5px;
}

.screen-label.tick {
  transform: translate(calc(-100% - 6px), -50%);
  font-size: 11px;
  color: #6b7d93;
}

.three-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: #64748b;
}
</style>
