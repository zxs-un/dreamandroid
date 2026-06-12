# DreamHub

**Run Stable Diffusion on recent Android devices**

> [!NOTE]
> This is a **fork** of [xororz/local-dream](https://github.com/xororz/local-dream),
> focused on **build CI** and **UI redesign**.
>
> This fork adapts the codebase to older Android SDK releases and 
> removes experimental Material3 APIs that were present in upstream.

---

## Version

The app version lives in **[VERSION_NAME](VERSION_NAME)** and **[VERSION_CODE](VERSION_CODE)** at the repository root:

```
VERSION_NAME     → 2026.06.12.16.01    # versionName: YYYY.MM.DD.HH.mm (UTC)
VERSION_CODE     → 244                 # versionCode: Google-style +1 per release
```

`build.gradle.kts` reads these files to set `versionName` (used as-is) and
`versionCode`.  CI enforces the format at
the start of each build and fails immediately if VERSION_NAME does not match the
pattern.

---

## Credits

This project is a fork of [xororz/local-dream](https://github.com/xororz/local-dream) and is built on top of many excellent open-source projects.

### C++ Libraries

- **[Qualcomm QNN SDK](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk)** — NPU model execution
- **[alibaba/MNN](https://github.com/alibaba/MNN/)** — CPU model execution
- **[xtensor-stack](https://github.com/xtensor-stack)** — Tensor operations & scheduling
- **[mlc-ai/tokenizers-cpp](https://github.com/mlc-ai/tokenizers-cpp)** — Text tokenization
- **[yhirose/cpp-httplib](https://github.com/yhirose/cpp-httplib)** — HTTP server
- **[nothings/stb](https://github.com/nothings/stb)** — Image processing
- **[facebook/zstd](https://github.com/facebook/zstd)** — Model compression
- **[nlohmann/json](https://github.com/nlohmann/json)** — JSON processing

### Android Libraries

- **[square/okhttp](https://github.com/square/okhttp)** — HTTP client
- **[coil-kt/coil](https://github.com/coil-kt/coil)** — Image loading & processing
- **[MoyuruAizawa/Cropify](https://github.com/MoyuruAizawa/Cropify)** — Image cropping
- **AOSP, Material Design, Jetpack Compose** — UI framework

### Models

- **[CompVis/stable-diffusion](https://github.com/CompVis/stable-diffusion)** and all other model creators
- **[xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)** — Image upscaling
- **[Kim2091/UltraSharpV2](https://huggingface.co/Kim2091/UltraSharpV2)** — Image upscaling
- **[bhky/opennsfw2](https://github.com/bhky/opennsfw2)** — NSFW content filtering

---

## License

This project inherits the license of the original [xororz/local-dream](https://github.com/xororz/local-dream) repository at fork time: CC-BY-NC. See [LICENSE](LICENSE).

---

## User Guide (from upstream xororz/local-dream)

For certain reasons, all guides and documentation from upstream are at  [Guide Site](https://ld-guide.chino.icu).
