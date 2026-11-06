import { apiClient } from './client';
import { TargetRoleResponse } from '../types/role';

export const roleApi = {
  listTargetRoles: async (): Promise<TargetRoleResponse[]> => {
    const response = await apiClient.get<TargetRoleResponse[]>('/roles');
    return response.data;
  },

  getTargetRole: async (id: number): Promise<TargetRoleResponse> => {
    const response = await apiClient.get<TargetRoleResponse>(`/roles/${id}`);
    return response.data;
  },
};
