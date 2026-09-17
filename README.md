# 3Double D

A photorealistic 3D avatar design studio for Android — Kotlin, Jetpack Compose and Google Filament.
This is the complete application: rendering, glTF import, on-device scan reconstruction, material
authoring, animation playback, export and audio all run on the device. Nothing in the render,
import, scan, export or audio paths is stubbed or simulated.

## What is in the build

| Area | Implementation |
| --- | --- |
| Renderer | Filament engine with a `UiHelper`-managed swap chain and a Compose frame-clock render loop |
| Materials | Compiled on device by **filamat** (`MaterialBuilder`) from GLSL source in `MaterialFactory`; no pre-baked `.filamat` blobs are shipped |
| glTF loading | In-project glTF 2.0 / GLB reader (`data/gltf/GltfDocument.kt`): `.glb` containers, embedded and data-URI buffers, integer-normalised accessors, node transforms baked in, per-primitive morph targets and material factors. Geometry is uploaded straight into Filament vertex/index buffers. |
| Base rigs | Four humanoid GLBs (`female`, `male`, `androgynous`, `cyborg`) generated at authoring time with real geometry, UVs, normals and seven morph targets each |
| Morphing | Evaluated on the CPU and re-uploaded to the position buffer, so any target count works (Filament's `MorphTargetBuffer` JNI call caps the update size below a real character mesh) |
| Scan | Shape-from-silhouette: CameraX capture → Otsu/border background segmentation → voxel carving → surfaced textured GLB |
| Export | PNG (offscreen `RenderTarget` + `readPixels`), turntable MP4 (MediaCodec surface render + MediaMuxer), animated GIF (in-house median-cut + LZW encoder), baked GLB (morph weights written into the binary buffer) |
| Audio | Real-time generative ambient pad (`AudioTrack` stream synthesis), synthesised interface cues, vocal recording and pitch-shifted playback |
| Content gate | Date-of-birth age verification with an explicit 18+ policy, failing closed |
| Persistence | Room for avatars/library, DataStore for settings and the age gate |

## Modules

```
app/src/main/java/com/threedd/studio
├── audio/       ambient synthesiser, cue generation, vocal bank
├── content/     age gate and content policy
├── data/        models, presets, Room, DataStore, repositories
├── export/      PNG / MP4 / GIF / GLB exporters
├── render/      Filament engine, materials, environment, camera, loader, capture
├── scan/        silhouette extraction, visual hull, glTF mesh writer, pipeline
└── ui/          Compose theme, navigation and every screen
```

## Building

```bash
# Android Studio: File > Open > this directory, then run the app module.
# Command line (Gradle wrapper):
./gradlew :app:assembleDebug
```

Requirements: JDK 17, Android SDK 35, Build Tools 35, and a device or emulator with OpenGL ES 3.0
or Vulkan. The debug variant installs alongside any release build.

## Permissions

`CAMERA` is required only for the scan screen. `RECORD_AUDIO` is required only when recording a vocal
sample. Exports write to app-private storage and are additionally published to the device gallery.

## Privacy

Scans, imported models, recordings and exports never leave the device.

## Licences

Filament and filamat are Apache-2.0. The generated base rigs are original work shipped under CC0.

## Scope of the glTF reader

The bundled reader covers what the studio needs: static triangle geometry, per-primitive
material factors, vertex colours, morph targets and baked node transforms. It deliberately
does **not** implement skeletal skinning, glTF animation clips or texture-map sampling -
imported models therefore arrive as static meshes shaded by the studio PBR material. The
built-in rigs and every studio feature work through the same path.

## Continuous build

A GitHub Actions workflow (`.github/workflows/android.yml`) builds the debug APK on every push
to `main`, runs the unit tests, and uploads the APK as the `3DoubleD-debug-apk` artifact.
The artifact can be downloaded from the run page or with `gh run download`.
