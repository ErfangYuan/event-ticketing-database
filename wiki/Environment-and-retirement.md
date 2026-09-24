# Environment and retirement

The original deployment environment is not carried into this public repository. Old environment files, deployment workflows and replaced documentation are backed up under the source project's ignored local/publication-backup-2026-09-24 directory. The public repository begins with fresh Git history and a configuration template only.

No old GitHub Actions deployment secrets or environments are imported. No automatic deployment pipeline runs on push. Configure new service accounts/keys for a new installation; never reuse a classroom service-role key. Private source documents, grading credentials, SSH material and database snapshots are excluded.

Local retirement does not revoke shared credentials in Google, Stripe, OpenAI or Supabase, and does not modify the original organization's remote repository. A service owner must coordinate cloud rotation and removal of secrets from that original deployment separately. The backup is for recovery, not redistribution.

Keep local/ ignored. To recover a local configuration, copy only the required file from the backup after reviewing its purpose; do not add the backup or an old .env to Git.
