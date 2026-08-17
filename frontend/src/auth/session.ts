import type { User } from '../api/types'

const KEY = 'bis.user'

/**
 * The only thing this app keeps in the browser: who is signed in.
 *
 * <p>Deliberately not the project data. The reference mock kept its whole database in
 * localStorage, which is exactly what makes resume, multi-tab and restart correctness impossible —
 * everything else here is asked for from the server.
 */
export function loadUser(): User | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function saveUser(user: User): void {
  localStorage.setItem(KEY, JSON.stringify(user))
}

export function clearUser(): void {
  localStorage.removeItem(KEY)
}
