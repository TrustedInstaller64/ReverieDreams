# Fabric 1.21.8 fixes

Approved scope: LTS b70aacb; fix confirmed Reverie/Nota lifecycle defects; preserve Gungnir trident gameplay; add a native Dialog keyboard music selector with list, pagination, selection and playback. No new master gameplay; Moon Damage behavior is explained but not changed without instruction. Other mods remain separate.

1. Pin Minecraft 1.21.8 and compile with Java 21 using the existing wrapper and Mojang mappings.
2. Add focused regressions for canceled/throwing delayed tasks and destroyed music players; run against the original implementations before fixing.
3. Cancel stale music sessions and prevent chunk loading; clean up block/entity/server lifecycle; close directory streams and bound invalid-song retries. Keep existing names and formatting.
4. Reuse MultiActionDialog, existing command registration and PLAYING_MUSIC component for keyboard selection; preserve ordinary playback and non-player instrument use.
5. Preserve Gungnir thrown-trident mechanics and check 1.21.8 client representation and animation; do not introduce spear components.
6. Build, run regressions, check packaged metadata and run a dedicated-server startup smoke test where dependencies permit. Document client/manual validation still needed.

Regression focus: unloaded chunk queries must not load chunks; same-position replacement cannot be removed by stale tasks; stopped tasks never run; exceptions do not replay one-shot tasks; destroy cancels timers/registrations; player GUI acts only on the current held keyboard; pages clamp and invalid song names are rejected.
