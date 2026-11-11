const API_BASE = import.meta.env.VITE_API_URL || ''

export async function api(path, { method = 'GET', body, token } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${API_BASE}/api${path}`, {
      method,
      headers,
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
  } catch {
    throw new Error('Could not reach StudyMate. Check that the backend is running.')
  }

  if (!response.ok) {
    const result = await response.json().catch(() => ({}))
    throw new Error(result.message || `Request failed (${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}