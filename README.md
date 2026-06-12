# dreamandroid

**Android Stable Diffusion with Snapdragon NPU acceleration — SDK Migration Fork**

> [!NOTE]
> This is a **fork** of [xororz/local-dream](https://github.com/xororz/local-dream),
> focused on **SDK migration and build system hardening**.
>
> This fork does **not** add new features.  It adapts the codebase to older Android
> SDK releases and removes experimental Material3 APIs that were present in upstream.

---

## Version

The app version lives in the **[VERSION](VERSION)** file at the repository root:

```
2026.06.12.06.00
```

Format: `YYYY.MM.DD.HH.mm` — year, month, day, hour, minute (UTC, zero-padded).

`build.gradle.kts` reads this file and derives both `versionName` (used as-is) and
`versionCode` (Unix timestamp, required by Android).  CI enforces the format at
the start of each build and fails immediately if VERSION does not match the
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

This project inherits the license of the original [xororz/local-dream](https://github.com/xororz/local-dream) repository.
