<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>行程规划</h1>
        <p>AI 智能规划您的旅行</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model.trim="formData.username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
            required
          />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <div class="form-options">
          <label class="remember-me">
            <input type="checkbox" v-model="formData.rememberMe" />
            <span>记住我</span>
          </label>
        </div>

        <p v-if="authStore.errorMessage" class="error-msg">{{ authStore.errorMessage }}</p>

        <button type="submit" class="btn-login" :disabled="authStore.loading">
          {{ authStore.loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="login-footer">
        <p>还没有账号？<router-link to="/register">立即注册</router-link></p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formData = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const handleLogin = async () => {
  if (!formData.username) return

  try {
    await authStore.login({
      username: formData.username,
      password: formData.password,
      rememberMe: formData.rememberMe
    })
    router.push('/dashboard')
  } catch (e) {
    console.error('登录失败', e)
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #FFFFFF 0%, #F7FBFF 50%, #F4F9FF 100%);
  padding: 40px 20px;
}

.login-container {
  width: 100%;
  max-width: 440px;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.login-header h1 {
  margin: 0 0 10px;
  font-size: 32px;
  font-weight: 700;
  color: #3385FF;
  letter-spacing: 1px;
}

.login-header p {
  margin: 0;
  color: #7A9AC4;
  font-size: 15px;
}

.login-form {
  background: #fff;
  border-radius: 20px;
  padding: 36px 32px;
  box-shadow: 0 4px 24px rgba(51, 133, 255, 0.08);
  border: 1px solid rgba(92, 168, 255, 0.1);
}

.form-group {
  margin-bottom: 22px;
}

.form-group label {
  display: block;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #2D4A6F;
}

.form-group input {
  width: 100%;
  padding: 14px 16px;
  border: 1.5px solid rgba(92, 168, 255, 0.15);
  border-radius: 12px;
  font-size: 15px;
  color: #2D4A6F;
  background: #FAFCFF;
  transition: all 0.2s;
  box-sizing: border-box;
  height: 50px;
}

.form-group input:focus {
  outline: none;
  border-color: #3385FF;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.1);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #6B8CBE;
}

.error-msg {
  margin: 0 0 20px;
  padding: 12px 16px;
  background: #FFF5F5;
  color: #E53E3E;
  border-radius: 10px;
  font-size: 14px;
  border-left: 3px solid #E53E3E;
}

.btn-login {
  width: 100%;
  padding: 15px;
  height: 52px;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.35);
}

.btn-login:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.45);
}

.btn-login:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  margin-top: 28px;
  color: #7A9AC4;
  font-size: 14px;
}

.login-footer a {
  color: #3385FF;
  text-decoration: none;
  font-weight: 600;
}

.login-footer a:hover {
  text-decoration: underline;
}
</style>
