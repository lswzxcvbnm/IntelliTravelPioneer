export function getAuthToken(): string {
  return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
}

export function getAuthTokenOrGuest(): string {
  return getAuthToken() || 'guest-token'
}
