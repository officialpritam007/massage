# Runtime fixes

- Haze 1.5.4 GlassCard effects now provide the required resolved `backgroundColor`, preventing the startup `IllegalArgumentException: backgroundColor not specified` crash.
- Startup recovery diagnostics remain enabled so any future launch exception can be captured instead of failing silently.
