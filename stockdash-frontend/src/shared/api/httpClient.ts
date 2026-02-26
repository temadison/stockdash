import type { ProblemDetails } from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export async function httpGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`);
  return toJson<T>(response);
}

export async function httpPostJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return toJson<T>(response);
}

export async function httpPostForm<T>(path: string, form: FormData): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: form
  });
  return toJson<T>(response);
}

async function toJson<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }

  let details: ProblemDetails | null = null;
  try {
    details = (await response.json()) as ProblemDetails;
  } catch {
    details = null;
  }

  const message = details?.detail ?? details?.title ?? `Request failed (${response.status})`;
  throw new Error(message);
}
