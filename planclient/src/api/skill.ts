import { axios } from '../utils/request'
import { API_MODULE } from './_prefix'

const SKILL_MODULE = `${API_MODULE}/skill`

export interface SkillVO {
    id: string
    userId: string
    name: string
    description: string
    prompt: string
    references: Record<string, string>
    scripts: Record<string, string>
    isPublic: boolean
    createTime: string
    updateTime: string
}

export interface SkillSaveRequest {
    name: string
    description: string
    prompt: string
    references: Record<string, string>
    scripts: Record<string, string>
    isPublic: boolean
}

export const createSkill = (data: SkillSaveRequest) =>
    axios.post(SKILL_MODULE, data).then(r => r.data)

export const updateSkill = (skillId: string, data: SkillSaveRequest) =>
    axios.put(`${SKILL_MODULE}/${skillId}`, data).then(r => r.data)

export const deleteSkill = (skillId: string) =>
    axios.delete(`${SKILL_MODULE}/${skillId}`).then(r => r.data)

export const getSkill = (skillId: string) =>
    axios.get(`${SKILL_MODULE}/${skillId}`).then(r => r.data)

export const listMySkills = () =>
    axios.get(`${SKILL_MODULE}/mine`).then(r => r.data)

export const listPublicSkills = () =>
    axios.get(`${SKILL_MODULE}/public`).then(r => r.data)
