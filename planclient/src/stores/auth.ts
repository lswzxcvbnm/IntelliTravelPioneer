import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getInformation, type UserVO } from '../api/user'

export const useAuthStore = defineStore('auth', () => {
  const storage = {
    getToken: () => localStorage.getItem('token') || sessionStorage.getItem('token') || '',
    getUser: () => JSON.parse(localStorage.getItem('user') || sessionStorage.getItem('user') || 'null'),
    setToken: (token: string, rememberMe: boolean) => {
      sessionStorage.removeItem('token')
      localStorage.removeItem('token')
      if (rememberMe) {
        localStorage.setItem('token', token)
      } else {
        sessionStorage.setItem('token', token)
      }
    },
    setUser: (user: UserVO | null, rememberMe: boolean) => {
      sessionStorage.removeItem('user')
      localStorage.removeItem('user')
      if (rememberMe) {
        localStorage.setItem('user', JSON.stringify(user))
      } else {
        sessionStorage.setItem('user', JSON.stringify(user))
      }
    },
    clear: () => {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }

  const user = ref<UserVO | null>(storage.getUser())
  const token = ref(storage.getToken())
  const loading = ref(false)
  const errorMessage = ref('')

  const normalizeApiMessage = (payload: any) => {
    return payload?.message || payload?.msg || ''
  }

  const isApiSuccess = (payload: any) => {
    const code = payload?.code
    return payload?.success === true || String(code) === '200' || String(code) === '0' || String(code) === '1'
  }

  const fetchUser = async (username: string) => {
    try {
      const userRes = await getInformation(username)
      if (isApiSuccess(userRes.data) && userRes.data?.data) {
        user.value = userRes.data.data
        const hasToken = localStorage.getItem('token') || sessionStorage.getItem('token')
        storage.setUser(user.value, !!hasToken && !!localStorage.getItem('token'))
        return user.value
      }
    } catch (e) {
      console.error('拉取用户信息失败:', e)
    }
    return null
  }

  const login = async (userData: { username: string; password?: string; rememberMe?: boolean }) => {
    const rememberMe = userData.rememberMe ?? false
    loading.value = true
    errorMessage.value = ''

    try {
      const loginRes = await loginApi({
        username: userData.username,
        password: userData.password ?? ''
      })

      if (!isApiSuccess(loginRes.data)) {
        const backendMessage = normalizeApiMessage(loginRes.data) || '登录失败，请检查账号密码'
        throw new Error(backendMessage)
      }

      const backendToken = loginRes.data.data
      token.value = backendToken || ''

      if (!token.value) {
        throw new Error('登录成功但未返回令牌')
      }

      storage.setToken(token.value, rememberMe)

      try {
        const userRes = await getInformation(userData.username)
        if (isApiSuccess(userRes.data) && userRes.data?.data) {
          user.value = userRes.data.data
        } else {
          user.value = { username: userData.username }
        }
      } catch (profileError) {
        console.warn('用户信息拉取失败，使用最小用户信息兜底:', profileError)
        user.value = { username: userData.username }
      }

      storage.setUser(user.value, rememberMe)
    } catch (error) {
      user.value = null
      token.value = ''
      storage.clear()
      errorMessage.value = (error as Error)?.message || '登录失败，请检查账号密码或稍后重试'
      throw new Error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  const logout = () => {
    user.value = null
    token.value = ''
    storage.clear()
  }

  return { user, token, loading, errorMessage, login, logout, fetchUser }
})
