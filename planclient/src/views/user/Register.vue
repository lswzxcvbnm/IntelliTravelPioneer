<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-header">
        <h1>创建账号</h1>
        <p>加入我们，开启智能旅行规划之旅</p>
      </div>

      <form @submit.prevent="handleRegister" class="register-form">
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
            autocomplete="new-password"
          />
        </div>

        <div class="form-group">
          <label>确认密码</label>
          <input
            v-model="formData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
          />
        </div>

        <div class="password-hints" v-if="formData.password">
          <div class="hint-item" :class="{ valid: hasMinLength, invalid: !hasMinLength && formData.password.length > 0 }">
            <span class="hint-icon">{{ hasMinLength ? '✓' : '○' }}</span>
            <span>至少 6 个字符</span>
          </div>
        </div>

        <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success-msg">{{ successMessage }}</p>

        <button type="submit" class="btn-register" :disabled="isSubmitting">
          {{ isSubmitting ? '注册中...' : '注册' }}
        </button>
      </form>

      <div class="register-footer">
        <p>已有账号？<router-link to="/login">立即登录</router-link></p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register as registerApi } from '../../api/user'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const hasMinLength = computed(() => formData.password.length >= 6)

const validateForm = (): string => {
  if (!formData.username) return '请输入用户名'
  if (formData.username.length < 3) return '用户名至少需要 3 个字符'
  if (!/^[a-zA-Z0-9_]+$/.test(formData.username)) return '用户名只能包含字母、数字和下划线'
  if (!formData.password) return '请输入密码'
  if (formData.password.length < 6) return '密码至少需要 6 个字符'
  if (formData.password !== formData.confirmPassword) return '两次输入的密码不一致'
  return ''
}

const handleRegister = async () => {
  errorMessage.value = ''
  successMessage.value = ''

  const error = validateForm()
  if (error) {
    errorMessage.value = error
    return
  }

  isSubmitting.value = true

  try {
    await registerApi({
      username: formData.username,
      password: formData.password
    })

    successMessage.value = '注册成功，正在为你登录...'

    setTimeout(async () => {
      try {
        await authStore.login({
          username: formData.username,
          password: formData.password,
          rememberMe: true
        })
        router.push('/dashboard')
      } catch (_loginError) {
        successMessage.value = '注册成功！正在跳转到登录页...'
        setTimeout(() => {
          router.push('/login')
        }, 1000)
      }
    }, 800)
  } catch (err: any) {
    const msg = err?.response?.data?.msg
      || err?.response?.data?.message
      || err?.message
      || '注册失败，请稍后重试'
    errorMessage.value = msg
    isSubmitting.value = false
    successMessage.value = ''
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #FFFFFF 0%, #F7FBFF 50%, #F4F9FF 100%);
  padding: 40px 20px;
}

.register-container {
  width: 100%;
  max-width: 440px;
}

.register-header {
  text-align: center;
  margin-bottom: 40px;
}

.register-header h1 {
  margin: 0 0 10px;
  font-size: 32px;
  font-weight: 700;
  color: #3385FF;
  letter-spacing: 1px;
}

.register-header p {
  margin: 0;
  color: #7A9AC4;
  font-size: 15px;
}

.register-form {
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

.form-group input::placeholder {
  color: #B0C4DE;
}

.password-hints {
  margin: -6px 0 20px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.hint-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94A3B8;
  transition: color 0.2s;
}

.hint-item.valid {
  color: #10B981;
}

.hint-item.invalid {
  color: #EF4444;
}

.hint-icon {
  font-size: 13px;
  width: 16px;
  text-align: center;
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

.success-msg {
  margin: 0 0 20px;
  padding: 12px 16px;
  background: #F0FFF4;
  color: #16A34A;
  border-radius: 10px;
  font-size: 14px;
  border-left: 3px solid #10B981;
}

.btn-register {
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

.btn-register:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.45);
}

.btn-register:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.register-footer {
  text-align: center;
  margin-top: 28px;
  color: #7A9AC4;
  font-size: 14px;
}

.register-footer a {
  color: #3385FF;
  text-decoration: none;
  font-weight: 600;
}

.register-footer a:hover {
  text-decoration: underline;
}
</style>
