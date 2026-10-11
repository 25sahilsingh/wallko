# Risks and how we handle them

← [Back to the plan index](PLAN.md)

Known risks and the fallback for each.

| Risk | Plan |
|---|---|
| AIY accuracy is middling on messy photos | Top-3 confirm UI, retake tips, confidence shown as leaves not percentages. Classifier is swappable. |
| Style transfer output looks muddy on some photos | Three styles to choose from; segmentation cut-out helps a lot; fallback is a tinted type-based sprite if the mask is empty. |
| ML Kit segmentation is beta and needs Play Services | Fallback: no mask, centre-crop with a soft oval vignette. |
| Health Connect missing on some phones | Fallback: built-in `TYPE_STEP_COUNTER` sensor read on app open. |
| GPS drift creates fake "new cells" | Ignore points with accuracy > 30 m; require 2 consecutive points in a cell. |
| Hilt + AGP 9 friction | Verified in Phase 0 before any feature work; manual DI fallback. |
| Perenual coverage is thin for wild species | Estimates + type defaults with provenance tags; "~" shown on card. |
| Battery during walks | Location updates every 5 s / 10 m, service stops on Stop or after 4 h. |
