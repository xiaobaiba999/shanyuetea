/* 员工模块 mock：严格模拟 /admin/employee/* 行为 */
import type { EmployeeDTO, EmployeePageQuery, EmployeeVO, LoginBody, LoginResult, PageResult, PasswordEditBody, Result } from '@/types'
import { TOKEN_KEY } from '@/utils/request'
import { clone, dbStore, fail, nextId, now, ok } from './db'
import type { SeedEmployee } from './seed'

function stripPassword(e: SeedEmployee): EmployeeVO {
  const { password: _pw, ...vo } = e
  return vo as EmployeeVO
}

export function mockLogin(body: LoginBody): Result<LoginResult> {
  const list = dbStore.employees.load()
  const emp = list.find((e) => e.username === body.username?.trim())
  if (!emp || emp.password !== body.password) {
    return fail('用户名或密码错误')
  }
  if (emp.status === 0) {
    return fail('该账号已被禁用，请联系管理员')
  }
  return ok({
    id: emp.id,
    name: emp.name,
    userName: emp.username,
    token: `mock-token-${emp.id}-${Date.now()}`,
  })
}

export function mockLogout(): Result<null> {
  return ok(null, '退出成功')
}

export function mockEmployeePage(query: EmployeePageQuery): Result<PageResult<EmployeeVO>> {
  let list = dbStore.employees.load()
  const name = query.name?.trim()
  if (name) list = list.filter((e) => e.name.includes(name))
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = list.slice(start, start + query.pageSize).map(stripPassword)
  return ok({ total, records })
}

export function mockGetEmployeeById(id: number): Result<EmployeeVO> {
  const emp = dbStore.employees.load().find((e) => e.id === id)
  if (!emp) return fail('员工不存在')
  return ok(stripPassword(emp))
}

export function mockAddEmployee(dto: EmployeeDTO): Result<null> {
  const list = dbStore.employees.load()
  if (list.some((e) => e.username === dto.username.trim())) {
    return fail('用户名已存在')
  }
  const emp: SeedEmployee = {
    id: nextId('employee'),
    name: dto.name.trim(),
    username: dto.username.trim(),
    phone: dto.phone.trim(),
    sex: dto.sex,
    idNumber: dto.idNumber,
    status: 1,
    createTime: now(),
    updateTime: now(),
    // 演示说明：新员工不设初始密码，仅 admin 账号可登录（教师演示账号 admin/123456）
  }
  list.push(emp)
  dbStore.employees.save(list)
  return ok(null)
}

export function mockUpdateEmployee(dto: EmployeeDTO): Result<null> {
  const list = dbStore.employees.load()
  const emp = list.find((e) => e.id === dto.id)
  if (!emp) return fail('员工不存在')
  const username = dto.username.trim()
  if (list.some((e) => e.id !== dto.id && e.username === username)) {
    return fail('用户名已存在')
  }
  emp.name = dto.name.trim()
  emp.username = username
  emp.phone = dto.phone.trim()
  emp.sex = dto.sex
  emp.idNumber = dto.idNumber
  emp.updateTime = now()
  dbStore.employees.save(list)
  return ok(null)
}

export function mockEmployeeStatus(status: 0 | 1, id: number): Result<null> {
  const list = dbStore.employees.load()
  const emp = list.find((e) => e.id === id)
  if (!emp) return fail('员工不存在')
  if (emp.username === 'admin') return fail('内置演示账号不允许禁用')
  emp.status = status
  emp.updateTime = now()
  dbStore.employees.save(list)
  return ok(null)
}

/**
 * 修改密码（mock 版，对齐后端 9.5 节契约）：
 * empId 不信任前端 —— 从 mock token（mockLogin 生成：mock-token-{id}-{ts}）里解析当前登录人；
 * 旧密码明文比对，通过后更新密码。真实后端则由 Controller 用 BaseContext 覆盖 empId。
 */
export function mockEditPassword(body: PasswordEditBody): Result<null> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const match = token.match(/^mock-token-(\d+)-/)
  const empId = match ? Number(match[1]) : 0
  const list = dbStore.employees.load()
  const emp = list.find((e) => e.id === empId)
  if (!emp) return fail('登录状态异常，请重新登录')
  if (!emp.password || emp.password !== body.oldPassword) return fail('旧密码不正确')
  emp.password = body.newPassword
  emp.updateTime = now()
  dbStore.employees.save(list)
  return ok(null)
}
