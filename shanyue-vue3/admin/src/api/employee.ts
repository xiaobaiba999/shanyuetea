/* 员工管理接口（契约：/api/employee/*，vite/nginx 将 /api 重写为 /admin 转发 8080） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockAddEmployee,
  mockEditPassword,
  mockEmployeePage,
  mockEmployeeStatus,
  mockGetEmployeeById,
  mockLogin,
  mockLogout,
  mockUpdateEmployee,
} from '@/mock/employee'
import type { EmployeeDTO, EmployeePageQuery, EmployeeVO, LoginBody, LoginResult, PageResult, PasswordEditBody } from '@/types'

/** 员工登录 POST /api/employee/login → data: {id, userName, name, token} */
export async function login(body: LoginBody): Promise<LoginResult> {
  if (USE_MOCK) {
    // 模拟真实网络延迟，登录反馈更自然
    await new Promise((r) => setTimeout(r, 350))
    return unwrapMock(mockLogin(body))
  }
  const res = await request.post<LoginResult>('/employee/login', body)
  return res.data
}

/** 退出登录 POST /api/employee/logout */
export async function logout(): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockLogout())
  const res = await request.post<null>('/employee/logout')
  return res.data
}

/** 新增员工 POST /api/employee */
export async function addEmployee(dto: EmployeeDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockAddEmployee(dto))
  const res = await request.post<null>('/employee', dto)
  return res.data
}

/** 员工分页查询 GET /api/employee/page?name=&page=&pageSize= → {total, records} */
export async function getEmployeePage(query: EmployeePageQuery): Promise<PageResult<EmployeeVO>> {
  if (USE_MOCK) return unwrapMock(mockEmployeePage(query))
  const res = await request.get<PageResult<EmployeeVO>>('/employee/page', { params: query })
  return res.data
}

/** 根据 ID 查询员工 GET /api/employee/{id} */
export async function getEmployeeById(id: number): Promise<EmployeeVO> {
  if (USE_MOCK) return unwrapMock(mockGetEmployeeById(id))
  const res = await request.get<EmployeeVO>(`/employee/${id}`)
  return res.data
}

/** 编辑员工 PUT /api/employee */
export async function updateEmployee(dto: EmployeeDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateEmployee(dto))
  const res = await request.put<null>('/employee', dto)
  return res.data
}

/** 启用/禁用员工 POST /api/employee/status/{status}?id= */
export async function updateEmployeeStatus(status: 0 | 1, id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockEmployeeStatus(status, id))
  const res = await request.post<null>(`/employee/status/${status}`, null, { params: { id } })
  return res.data
}

/** 修改密码 PUT /api/employee/editPassword（empId 后端从登录态覆盖，前端只传新旧密码；改密成功后应强制重新登录） */
export async function editPassword(body: PasswordEditBody): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockEditPassword(body))
  const res = await request.put<null>('/employee/editPassword', body)
  return res.data
}
