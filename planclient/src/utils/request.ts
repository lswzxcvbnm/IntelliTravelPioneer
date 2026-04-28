import axios from 'axios'
import { API_BASE_URL } from '../config/api'
import { getAuthToken } from './authToken'

const service = axios.create({
    baseURL: API_BASE_URL,
    timeout: 30000
})

function getToken() {
    return getAuthToken();
}

service.interceptors.request.use(
    config => {
        const token = getToken();
        if(token) {
            config.headers['token'] = token
        }
        return config
    },
    error => {
        console.log(error);
        return Promise.reject(error);
    }
)

service.interceptors.response.use(
    response => {
        if (response.status === 200) {
            return response;
        } else {
            return Promise.reject(new Error(`HTTP ${response.status}`));
        }
    },
    error => {
        const serverMessage = error?.response?.data?.message || error?.response?.data?.msg
        if (serverMessage) {
            error.message = serverMessage
        }

        console.error('Request failed:', {
            url: error?.config?.url,
            baseURL: error?.config?.baseURL,
            status: error?.response?.status,
            message: error?.message
        })

        return Promise.reject(error);
    }
)

export {
    service as axios
}
