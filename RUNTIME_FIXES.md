# Runtime fixes

- Haze 1.5.4 GlassCard effects now provide the required resolved `backgroundColor`, preventing the startup `IllegalArgumentException: backgroundColor not specified` crash.
- The Haze effect assigns that color through the `HazeEffectScope` receiver (`this.backgroundColor`) so the Kotlin compiler does not confuse it with the `GlassCard` parameter.
- Startup recovery diagnostics remain enabled so any future launch exception can be captured instead of failing silently.
