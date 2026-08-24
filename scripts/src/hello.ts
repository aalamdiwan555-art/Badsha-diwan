/**
 * Normalize a workspace package name for scripts that need to address a
 * package consistently across pnpm commands and log output.
 */
export function workspacePackageName(name: string): string {
  const normalized = name.trim();
  if (!normalized) {
    throw new Error("A workspace package name is required");
  }
  return normalized.startsWith("@workspace/")
    ? normalized
    : `@workspace/${normalized}`;
}