package com.threedd.studio.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.ai.PromptToAppearance
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.audio.VocalBank
import com.threedd.studio.content.AgeGate
import com.threedd.studio.data.avatar.AppearanceSpec
import com.threedd.studio.data.model.AnimationClip
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.LightState
import com.threedd.studio.data.model.MaterialState
import com.threedd.studio.data.model.ModelSource
import com.threedd.studio.data.model.Presets
import com.threedd.studio.data.model.QualityPreset
import com.threedd.studio.data.repository.AvatarRepository
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.data.settings.SettingsStore
import com.threedd.studio.render.StudioRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudioUiState(
    val models: List<AvatarModel> = emptyList(),
    val model: AvatarModel? = null,
    val bodyPresetId: String = "neutral_base",
    val morphWeights: Map<String, Float> = emptyMap(),
    val material: MaterialState = MaterialState(),
    val light: LightState = LightState(),
    val animations: List<AnimationClip> = emptyList(),
    val animationIndex: Int = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val loop: Boolean = true,
    val overrideModelMaterials: Boolean = true,
    val morphNames: List<String> = emptyList(),
    val appearance: AppearanceSpec = AppearanceSpec(),
    val prompt: String = "",
    val promptSummary: String? = null,
    val recording: Boolean = false,
    val vocals: List<VocalBank.Sample> = emptyList(),
    val matureUnlocked: Boolean = false,
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null
)

@HiltViewModel
class StudioViewModel @Inject constructor(
    private val models: ModelRepository,
    private val avatars: AvatarRepository,
    private val settings: SettingsStore,
    val renderer: StudioRenderer,
    private val audio: AudioEngine,
    private val vocalBank: VocalBank,
    private val ageGate: AgeGate
) : ViewModel() {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            models.importedModels.collect { imported ->
                _state.update { it.copy(models = ModelRepository.builtInModels + imported) }
            }
        }
        viewModelScope.launch {
            settings.snapshot.collect { snapshot ->
                _state.update {
                    it.copy(
                        speed = snapshot.animationSpeed,
                        loop = snapshot.loopAnimation,
                        matureUnlocked = snapshot.ageVerified
                    )
                }
                renderer.setQuality(snapshot.quality)
                audio.sfxEnabled = snapshot.sfxEnabled
                audio.volume = snapshot.volume
            }
        }
        viewModelScope.launch {
            val snapshot = settings.snapshot.first()
            renderer.setQuality(snapshot.quality)
            val first = _state.value.models.firstOrNull { !it.mature || snapshot.ageVerified }
            if (first != null) loadModel(first)
        }
    }

    fun loadModel(model: AvatarModel) {
        if (model.mature && !_state.value.matureUnlocked) {
            _state.update { it.copy(error = "Unlock the 18+ module to open this model.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val loaded = renderer.load(model)
            if (!loaded) {
                _state.update { it.copy(loading = false, error = "Could not load ${model.displayName}.") }
                return@launch
            }
            renderer.setMaterial(_state.value.material, _state.value.overrideModelMaterials)
            renderer.setMorphWeights(_state.value.morphWeights)
            renderer.applyAppearance(_state.value.appearance)
            val clips = renderer.models.animations()
            _state.update {
                it.copy(model = model, loading = false, animations = clips,
                    animationIndex = 0, playing = clips.isNotEmpty(),
                    morphNames = renderer.models.morphTargetNames,
                    status = "Loaded ${model.displayName}")
            }
            audio.play(AudioEngine.Cue.SELECT)
        }
    }

    /** Loads a model by id from whatever the studio currently knows about. */
    fun loadModelById(id: String) {
        val model = _state.value.models.firstOrNull { it.id == id }
            ?: ModelRepository.builtInModels.firstOrNull { it.id == id }
        if (model != null && model.id != _state.value.model?.id) loadModel(model)
    }

    fun selectBodyPreset(id: String) {
        val preset = Presets.preset(id) ?: return
        if (preset.mature && !_state.value.matureUnlocked) {
            _state.update { it.copy(error = "This preset is part of the 18+ module.") }
            return
        }
        val weights = preset.morphWeights
        val materialOverride = Presets.materialForFamily(preset.family) ?: _state.value.material
        _state.update {
            it.copy(bodyPresetId = id, morphWeights = weights, material = materialOverride)
        }
        renderer.setMorphWeights(weights)
        renderer.setMaterial(materialOverride, _state.value.overrideModelMaterials)
        audio.play(AudioEngine.Cue.TAP)
    }

    fun updateMaterial(material: MaterialState) {
        _state.update { it.copy(material = material) }
        renderer.setMaterial(material, _state.value.overrideModelMaterials)
    }

    fun applyMaterialPreset(name: String) {
        Presets.materialPresets[name]?.let { updateMaterial(it) }
        audio.play(AudioEngine.Cue.SELECT)
    }

    fun setOverrideModelMaterials(enabled: Boolean) {
        _state.update { it.copy(overrideModelMaterials = enabled) }
        if (enabled) renderer.setMaterial(_state.value.material, true) else renderer.useModelMaterials()
    }

    fun updateLight(light: LightState) {
        _state.update { it.copy(light = light) }
        renderer.updateLight(light)
    }

    fun applyLightPreset(name: String) {
        Presets.lightPresets[name]?.let { light ->
            _state.update { it.copy(light = light) }
            renderer.setLight(light)
        }
        audio.play(AudioEngine.Cue.SELECT)
    }

    fun selectAnimation(index: Int) {
        _state.update { it.copy(animationIndex = index, playing = true) }
    }

    fun togglePlayback() {
        _state.update { it.copy(playing = !it.playing) }
        audio.play(AudioEngine.Cue.TAP)
    }

    fun stopPlayback() = _state.update { it.copy(playing = false) }
    fun setSpeed(speed: Float) = _state.update { it.copy(speed = speed.coerceIn(0.1f, 3f)) }
    fun setLoop(loop: Boolean) = _state.update { it.copy(loop = loop) }
    fun resetCamera() {
        renderer.resetFraming()
        audio.play(AudioEngine.Cue.TAP)
    }

    // ---- describe an avatar in words ----

    fun setPrompt(value: String) = _state.update { it.copy(prompt = value) }

    /**
     * Builds an avatar from a written description. The parse is on device, so this is free and
     * works with no network and no model installed.
     */
    fun generateFromPrompt() {
        val text = _state.value.prompt
        if (text.isBlank()) {
            _state.update { it.copy(error = "Describe the avatar first, for example \"cyborg with pink hair and a visor\"") }
            return
        }
        val spec = PromptToAppearance.parse(text, _state.value.appearance)
        updateAppearance(spec)
        _state.update { it.copy(promptSummary = PromptToAppearance.summarise(spec), error = null, status = "Generated from description") }
        audio.play(AudioEngine.Cue.CONFIRM)
    }

    // ---- vocal takes ----

    fun refreshVocals() = _state.update { it.copy(vocals = vocalBank.samples) }

    fun startVocalRecording() {
        if (vocalBank.startRecording("take")) {
            _state.update { it.copy(recording = true, error = null) }
            audio.play(AudioEngine.Cue.TAP)
        } else {
            _state.update { it.copy(error = "Microphone unavailable - grant the RECORD_AUDIO permission.") }
            audio.play(AudioEngine.Cue.ERROR)
        }
    }

    fun stopVocalRecording() {
        val sample = vocalBank.stopRecording()
        _state.update {
            it.copy(
                recording = false,
                vocals = vocalBank.samples,
                status = if (sample != null) "Saved a vocal take" else null,
                error = if (sample == null) "Nothing was recorded." else null
            )
        }
    }

    fun playVocal(sample: VocalBank.Sample, semitones: Float) {
        vocalBank.play(sample, semitones)
    }

    /** Applies a customisation change: wearables rebuild, skin tone goes on the material. */
    fun updateAppearance(appearance: AppearanceSpec) {
        _state.update { it.copy(appearance = appearance, material = it.material.copy(baseColorHex = appearance.skinToneHex)) }
        renderer.applyAppearance(appearance)
    }

    /** Drives one morph target directly, used by the body sliders. */
    fun setMorphWeight(name: String, value: Float) {
        val updated = _state.value.morphWeights.toMutableMap()
        updated[name] = value.coerceIn(0f, 1f)
        _state.update { it.copy(morphWeights = updated) }
        renderer.setMorphWeights(updated)
    }

    /** Picks a random body preset, material preset and light rig. */
    fun randomize() {
        val preset = Presets.bodyPresets.filter { !it.mature || _state.value.matureUnlocked }.random()
        val materialName = Presets.materialPresets.keys.random()
        val lightName = Presets.lightPresets.keys.random()
        selectBodyPreset(preset.id)
        applyMaterialPreset(materialName)
        applyLightPreset(lightName)
        audio.play(AudioEngine.Cue.SELECT)
    }

    fun saveDesign(name: String) {
        val current = _state.value
        val model = current.model ?: return
        viewModelScope.launch {
            avatars.save(
                AvatarDesign(
                    name = name,
                    modelId = model.id,
                    bodyPresetId = current.bodyPresetId,
                    material = current.material,
                    light = current.light,
                    morphWeights = current.morphWeights,
                    mature = model.mature || current.bodyPresetId == "anatomical"
                )
            )
            _state.update { it.copy(status = "Saved \"$name\"") }
            audio.play(AudioEngine.Cue.CONFIRM)
        }
    }

    fun consumeStatus() = _state.update { it.copy(status = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun unlocked(value: Boolean) = _state.update { it.copy(matureUnlocked = value) }

    /** Advances animation time on the render frame. Called from the viewport on the UI thread. */
    fun advanceAnimation(deltaSeconds: Float) {
        val s = _state.value
        if (!s.playing || s.animations.isEmpty()) return
        val clip = s.animations.getOrNull(s.animationIndex) ?: return
        val scaled = deltaSeconds * s.speed
        var next = animationTime + scaled
        if (clip.durationSeconds > 0f) {
            if (next > clip.durationSeconds) {
                next = if (s.loop) next % clip.durationSeconds else clip.durationSeconds
                if (!s.loop) _state.update { it.copy(playing = false) }
            }
        }
        animationTime = next
        renderer.models.applyAnimation(s.animationIndex, next)
    }

    private var animationTime = 0f

    fun restartAnimation() {
        animationTime = 0f
        renderer.models.applyAnimation(_state.value.animationIndex, 0f)
    }

    override fun onCleared() {
        super.onCleared()
        renderer.detach()
    }

    val builtInModels: List<AvatarModel> get() = ModelRepository.builtInModels
    val sources: List<ModelSource> get() = ModelSource.entries.toList()
    val qualities: List<QualityPreset> get() = QualityPreset.entries.toList()
}
