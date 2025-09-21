import * as React from 'react';

interface LogoIconProps {
    className?: string;
}

export const LogoIcon: React.FC<LogoIconProps> = (props: LogoIconProps) => (
    <svg width="118" height="20" viewBox="0 0 118 20" fill="none" xmlns="http://www.w3.org/2000/svg" className={props.className}>
        <text x="0" y="15" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="var(--primary-text-color)">TRADEX</text>
    </svg>
);