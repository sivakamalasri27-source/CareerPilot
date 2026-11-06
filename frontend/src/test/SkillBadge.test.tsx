import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SkillBadge } from '../components/common/SkillBadge';

describe('SkillBadge Component', () => {
  it('renders VERIFIED status with green badge styling and label', () => {
    render(<SkillBadge status="VERIFIED" skillName="Java" />);
    const badge = screen.getByTestId('skill-badge');
    expect(badge).toHaveTextContent('VERIFIED');
    expect(badge).toHaveClass('bg-emerald-50');
    expect(badge).toHaveClass('text-emerald-700');
  });

  it('renders PARTIAL status with amber badge styling and label', () => {
    render(<SkillBadge status="PARTIAL" skillName="Docker" />);
    const badge = screen.getByTestId('skill-badge');
    expect(badge).toHaveTextContent('PARTIAL');
    expect(badge).toHaveClass('bg-amber-50');
    expect(badge).toHaveClass('text-amber-700');
  });

  it('renders UNVERIFIED status with gray badge styling and label', () => {
    render(<SkillBadge status="UNVERIFIED" skillName="Kubernetes" />);
    const badge = screen.getByTestId('skill-badge');
    expect(badge).toHaveTextContent('UNVERIFIED');
    expect(badge).toHaveClass('bg-slate-100');
    expect(badge).toHaveClass('text-slate-600');
  });

  it('renders evidence URL link when provided for verified skills', () => {
    render(
      <SkillBadge
        status="VERIFIED"
        skillName="Spring Boot"
        evidenceUrl="https://github.com/candidate/spring-microservices"
      />
    );
    const link = screen.getByRole('link', { name: /view evidence repository/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://github.com/candidate/spring-microservices');
  });
});
