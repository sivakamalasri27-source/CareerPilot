export interface RoleSkillDto {
  skillName: string;
  weight: number;
  priority: 'CORE' | 'IMPORTANT' | 'NICE_TO_HAVE';
}

export interface TargetRoleResponse {
  id: number;
  name: string;
  description: string;
  requiredSkillsCount: number;
  requiredSkills?: RoleSkillDto[];
}
