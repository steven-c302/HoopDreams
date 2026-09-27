# Final validation

- Cast: all 13 requested names, in order; Daniel appears twice, with distinct player IDs and faces.
- Visual review: every scene and transition checked, plus a contact sheet extracted from the encoded MP4. Fixed an initial logo collision before rendering.
- Gameplay: current TriviaStage, AvatarFace and Brainy components with staged data. Quick Draw, robbery and typed-answer reveal are animated captures. Score changes match the displayed awards and theft.
- Video: H.264, 1920×1080, 30 fps, 720 frames, 24.000 seconds. Full-file FFmpeg decode completed without errors.
- Sound: stereo AAC, 48 kHz, 24 seconds. Measured soundtrack −14.95 LUFS integrated, −4.27 dBTP true peak; no clipping. Existing Party OS score and stingers; no narration.
- Poster: settled roster frame at 4.5 seconds, also rendered as frame zero without extending the video.
- Renderer: TypeScript check passed; no browser page errors during the final render.
- README: updated poster/video/storyboard/caption links resolve locally; question counts checked against the current packs (302 / 62 / 23).
- Scope: existing source-code edits and earlier video outputs preserved. No application logic changed by this video task.
