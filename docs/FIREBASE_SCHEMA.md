# Firebase data model

- `users/{uid}`: email, displayName, createdAt, updatedAt
- `users/{uid}/favorites/{mangaId}`: mangaId, updatedAt
- `users/{uid}/readingProgress/{mangaId}`: chapterId, page, updatedAt
- `manga/{mangaId}`: title, description, coverUrl, author, genres[], status (`draft`, `pending_review`, `published`), createdBy, createdAt
- `manga/{mangaId}/chapters/{chapterId}`: title, chapterNumber, pageUrls[], publishedAt, status
- Storage: `manga/{mangaId}/chapters/{chapterId}/{pageNumber}.webp`

## Production requirements
1. Set admin custom claims from a trusted backend (Firebase Admin SDK).
2. Restrict publication and uploads to admins.
3. Verify publication rights and keep license/source metadata for each title.
4. Consider App Check, rate limits, backups, moderation, privacy policy, and account deletion.

## Fields consumed by the current Android catalog

The home and explore screens subscribe to `manga` where `status == "published"`. Each published document should include:

- `title` (string, required)
- `status` (string; set to `published` only after a moderator approves the title)
- `genre` (string, optional; the app displays `غير مصنّف` if absent)
- `rating` (number, optional)
- `chapterCount` (integer, optional; `chapters` is accepted as a legacy fallback)
- `description` (string, optional)

The app currently keeps the local demo catalog visible if Firebase is not configured, if no published documents exist, or if the listener fails. This fallback is intentional so the UI remains usable during setup. The reader looks for `manga/{mangaId}/chapters/{chapterNumber}` and reads `pageUrls` (preferred) or `pages` as an ordered array of HTTPS image URLs. Chapter documents should use the chapter number as the document ID (for example `1`, `2`, `3`) and only contain pages you have permission to distribute. The reader shows an explanatory empty state when no pages are published.
