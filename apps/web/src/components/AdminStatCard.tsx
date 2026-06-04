import type { CSSProperties, ReactNode } from "react";
import { Card, Statistic } from "antd";
import type { StatisticProps } from "antd";

export type AdminStatPalette = {
  backgroundColor: string;
  borderColor: string;
};

type AdminStatCardProps = {
  title: ReactNode;
  value: StatisticProps["value"];
  palette: AdminStatPalette;
  note?: ReactNode;
  className?: string;
  style?: CSSProperties;
  radius?: number;
  prefix?: ReactNode;
  suffix?: ReactNode;
  precision?: number;
  formatter?: StatisticProps["formatter"];
  valueStyle?: CSSProperties;
};

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export function AdminStatCard({
  title,
  value,
  palette,
  note,
  className,
  style,
  radius = 16,
  prefix,
  suffix,
  precision,
  formatter,
  valueStyle,
}: AdminStatCardProps) {
  return (
    <Card
      bordered={false}
      className={joinClassNames("admin-stat-card", className)}
      style={{
        backgroundColor: palette.backgroundColor,
        border: `1px solid ${palette.borderColor}`,
        borderRadius: radius,
        ...style,
      }}
    >
      <div className="admin-stat-card__content">
        <Statistic
          className="admin-stat-card__statistic"
          title={title}
          value={value}
          prefix={prefix}
          suffix={suffix}
          precision={precision}
          formatter={formatter}
          valueStyle={{
            fontSize: 30,
            lineHeight: 1.1,
            fontWeight: 700,
            ...valueStyle,
          }}
        />
        {note ? <div className="admin-stat-card__note">{note}</div> : null}
      </div>
    </Card>
  );
}
