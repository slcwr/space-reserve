export type Role = 'USER' | 'ADMIN'

export type User = {
  id: number
  email: string
  displayName: string
  role: Role
}

export function isAdmin(user: User): boolean {
  return user.role === 'ADMIN'
}
