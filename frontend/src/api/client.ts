import type { ApiResponse } from '../types'

const credentialKey = 'haiju-community-basic-auth'

export function setCredentials(username: string, password: string) {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  sessionStorage.setItem(credentialKey, btoa(binary))
}

export function clearCredentials() {
  sessionStorage.removeItem(credentialKey)
}

export function hasCredentials() {
  return Boolean(sessionStorage.getItem(credentialKey))
}

async function send<T>(path: string, init: RequestInit = {}): Promise<T> {
  const credential = sessionStorage.getItem(credentialKey)
  const headers = new Headers(init.headers)
  if (credential) headers.set('Authorization', `Basic ${credential}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(path, { ...init, headers })
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  if (!response.ok || !payload || payload.code !== 0) {
    if (response.status === 401) clearCredentials()
    throw new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
  }
  return payload.data
}

export const api = {
  get<T>(path: string) {
    return send<T>(path)
  },
  post<T>(path: string, body?: unknown) {
    return send<T>(path, { method: 'POST', body: body instanceof FormData ? body : JSON.stringify(body ?? {}) })
  },
  put<T>(path: string, body: unknown) {
    return send<T>(path, { method: 'PUT', body: JSON.stringify(body) })
  },
  delete<T>(path: string) {
    return send<T>(path, { method: 'DELETE' })
  },
}

export async function download(path: string, filename: string) {
  const credential = sessionStorage.getItem(credentialKey)
  const response = await fetch(path, { headers: credential ? { Authorization: `Basic ${credential}` } : {} })
  if (!response.ok) throw new Error(`导出失败（HTTP ${response.status}）`)
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
