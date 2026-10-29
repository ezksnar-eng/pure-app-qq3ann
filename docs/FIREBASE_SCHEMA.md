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
