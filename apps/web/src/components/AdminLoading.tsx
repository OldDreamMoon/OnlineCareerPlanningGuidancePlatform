import type { CSSProperties } from "react";
import { Card, Col, Row, Statistic } from "antd";
import type { AdminStatPalette } from "./AdminStatCard";

type AdminStatGridColProps = {
  xs?: number;
  sm?: number;
  md?: number;
  lg?: number;
  xl?: number;
};

type SkeletonBarProps = {
  width?: CSSProperties["width"];
  height?: number;
  inverse?: boolean;
  radius?: number;
};

const defaultStatCardPalettes: AdminStatPalette[] = [
  { backgroundColor: "#eef2ff", borderColor: "#c7d2fe" },
  { backgroundColor: "#ecfdf5", borderColor: "#a7f3d0" },
  { backgroundColor: "#fff7ed", borderColor: "#fdba74" },
  { backgroundColor: "#fef2f2", borderColor: "#fecaca" },
  { backgroundColor: "#f5f3ff", borderColor: "#ddd6fe" },
  { backgroundColor: "#ecfeff", borderColor: "#a5f3fc" },
];

function SkeletonBar({ width = "100%", height = 14, inverse = false, radius }: SkeletonBarProps) {
  return (
    <div
      className={`admin-skeleton-block${inverse ? " admin-skeleton-block-inverse" : ""}`}
      style={{ width, height, borderRadius: radius ?? Math.max(8, Math.min(999, Math.round(height / 2) + 6)) }}
    />
  );
}

export function AdminLoadingPanel({
  title,
  description,
  minHeight = 320,
  inverse = false,
}: {
  title: string;
  description: string;
  minHeight?: number;
  inverse?: boolean;
}) {
  return (
    <Card
      variant="borderless"
      className={`admin-loading-panel rounded-3xl shadow-sm${inverse ? " admin-loading-panel-inverse" : ""}`}
      styles={{ body: { padding: 0 } }}
    >
      <div className="flex flex-col gap-6 p-6" style={{ minHeight }}>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <div className={`admin-loading-title${inverse ? " admin-loading-title-inverse" : ""}`}>{title}</div>
            <div className={`admin-loading-description${inverse ? " admin-loading-description-inverse" : ""}`}>{description}</div>
          </div>
          <div className="flex flex-wrap gap-2">
            <SkeletonBar width={92} height={36} inverse={inverse} radius={14} />
            <SkeletonBar width={128} height={36} inverse={inverse} radius={14} />
          </div>
        </div>

        <div className={`rounded-2xl border p-4${inverse ? " border-white/12 bg-white/8" : " border-gray-100 bg-gray-50"}`}>
          <div className="space-y-3">
            <SkeletonBar width="26%" height={12} inverse={inverse} />
            <SkeletonBar width="100%" height={14} inverse={inverse} />
            <SkeletonBar width="92%" height={14} inverse={inverse} />
            <SkeletonBar width="68%" height={14} inverse={inverse} />
          </div>
        </div>

        <div className="flex flex-wrap gap-3">
          <SkeletonBar width={120} height={36} inverse={inverse} radius={18} />
          <SkeletonBar width={164} height={36} inverse={inverse} radius={18} />
          <SkeletonBar width={96} height={36} inverse={inverse} radius={18} />
        </div>
      </div>
    </Card>
  );
}

export function AdminPageHeaderSkeleton({
  withMeta = false,
  actionCount = 2,
}: {
  withMeta?: boolean;
  actionCount?: number;
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0 flex-1">
        <div className="space-y-3">
          <SkeletonBar width={152} height={16} radius={10} />
          <SkeletonBar width="42%" height={14} />
          {withMeta ? <SkeletonBar width="58%" height={12} /> : null}
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {Array.from({ length: actionCount }).map((_, index) => (
          <SkeletonBar key={`page-header-action-${index}`} width={index === 0 ? 108 : 132} height={36} radius={14} />
        ))}
      </div>
    </div>
  );
}

export function AdminStatGridSkeleton({
  count = 4,
  className = "",
  cardPalettes,
  colProps,
  showNotes = true,
  radius = 16,
}: {
  count?: number;
  className?: string;
  cardPalettes?: AdminStatPalette[];
  colProps?: AdminStatGridColProps;
  showNotes?: boolean;
  radius?: number;
}) {
  const activeCardPalettes = cardPalettes && cardPalettes.length > 0 ? cardPalettes : defaultStatCardPalettes;
  const activeCount = cardPalettes && cardPalettes.length > 0 ? cardPalettes.length : count;
  const activeColProps = colProps ?? { xs: 24, md: 12, xl: 6 };

  return (
    <Row gutter={[16, 16]} className={className}>
      {Array.from({ length: activeCount }).map((_, index) => (
        <Col {...activeColProps} key={`stat-skeleton-${index}`}>
          <AdminStatCardSkeleton
            palette={activeCardPalettes[index % activeCardPalettes.length]}
            note={showNotes}
            radius={radius}
            valueWidth={index % 2 === 0 ? "58%" : "46%"}
          />
        </Col>
      ))}
    </Row>
  );
}

export function AdminStatCardSkeleton({
  palette,
  note = true,
  className = "",
  style,
  radius = 16,
  titleWidth = "40%",
  titleHeight = 16,
  valueWidth = "58%",
  valueHeight = 40,
  noteWidth = "74%",
  noteHeight = 14,
  inverse = false,
}: {
  palette: AdminStatPalette;
  note?: boolean;
  className?: string;
  style?: CSSProperties;
  radius?: number;
  titleWidth?: CSSProperties["width"];
  titleHeight?: number;
  valueWidth?: CSSProperties["width"];
  valueHeight?: number;
  noteWidth?: CSSProperties["width"];
  noteHeight?: number;
  inverse?: boolean;
}) {
  return (
    <Card
      variant="borderless"
      className={`admin-stat-card rounded-2xl shadow-sm${className ? ` ${className}` : ""}`}
      style={{
        border: `1px solid ${palette.borderColor}`,
        backgroundColor: palette.backgroundColor,
        borderRadius: radius,
        ...style,
      }}
    >
      <div className="admin-stat-card__content">
        <Statistic
          className="admin-stat-card__statistic"
          title={<SkeletonBar width={titleWidth} height={titleHeight} inverse={inverse} />}
          value={0}
          formatter={() => (
            <div className="admin-stat-card__skeleton-value">
              <SkeletonBar width={valueWidth} height={valueHeight} inverse={inverse} radius={16} />
            </div>
          )}
        />
        {note ? (
          <div className="admin-stat-card__note">
            <SkeletonBar width={noteWidth} height={noteHeight} inverse={inverse} />
          </div>
        ) : null}
      </div>
    </Card>
  );
}

export function AdminTableSkeleton({
  rows = 6,
  columns = 4,
  showToolbar = true,
  filterCount = 3,
  showPagination = true,
}: {
  rows?: number;
  columns?: number;
  showToolbar?: boolean;
  filterCount?: number;
  showPagination?: boolean;
}) {
  return (
    <div className="space-y-4">
      {showToolbar ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-3">
            {Array.from({ length: filterCount }).map((_, index) => (
              <SkeletonBar key={`toolbar-filter-${index}`} width={index === 0 ? 176 : 132} height={38} radius={14} />
            ))}
          </div>
          <SkeletonBar width={128} height={38} radius={14} />
        </div>
      ) : null}

      <div className="space-y-3 rounded-2xl border border-gray-100 bg-white p-4">
        <div className="grid gap-3" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
          {Array.from({ length: columns }).map((_, index) => (
            <SkeletonBar key={`table-header-${index}`} width={`${70 - index * 6}%`} height={12} />
          ))}
        </div>

        {Array.from({ length: rows }).map((_, rowIndex) => (
          <div
            key={`table-row-${rowIndex}`}
            className="grid gap-3 rounded-xl border border-gray-100 bg-gray-50/70 px-4 py-3"
            style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
          >
            {Array.from({ length: columns }).map((__, columnIndex) => (
              <SkeletonBar
                key={`table-cell-${rowIndex}-${columnIndex}`}
                width={`${Math.max(38, 88 - ((rowIndex + columnIndex) % 5) * 10)}%`}
                height={14}
              />
            ))}
          </div>
        ))}
      </div>

      {showPagination ? (
        <div className="flex items-center justify-between gap-4">
          <SkeletonBar width={112} height={12} />
          <div className="flex items-center gap-2">
            <SkeletonBar width={86} height={34} radius={14} />
            <SkeletonBar width={124} height={34} radius={14} />
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function AdminCardStackSkeleton({
  count = 3,
  inverse = false,
}: {
  count?: number;
  inverse?: boolean;
}) {
  return (
    <div className="space-y-4">
      {Array.from({ length: count }).map((_, index) => (
        <Card
          key={`card-stack-skeleton-${index}`}
          bordered={false}
          className={`rounded-2xl shadow-sm${inverse ? " border border-white/10 bg-white/10" : " border border-gray-100 bg-white"}`}
        >
          <div className="space-y-3">
            <SkeletonBar width="34%" height={12} inverse={inverse} />
            <SkeletonBar width={index % 2 === 0 ? "52%" : "68%"} height={28} inverse={inverse} radius={14} />
            <SkeletonBar width="86%" height={12} inverse={inverse} />
            <SkeletonBar width="72%" height={12} inverse={inverse} />
          </div>
        </Card>
      ))}
    </div>
  );
}

export function AdminDetailSkeleton({
  statCards = 0,
  mainSections = 3,
  sideSections = 0,
}: {
  statCards?: number;
  mainSections?: number;
  sideSections?: number;
}) {
  const renderSectionCard = (key: string) => (
    <Card key={key} bordered={false} className="admin-loading-card rounded-2xl shadow-sm">
      <div className="space-y-4">
        <div className="space-y-2">
          <SkeletonBar width="34%" height={14} />
          <SkeletonBar width="76%" height={12} />
        </div>
        <div className="space-y-3">
          <SkeletonBar width="100%" height={14} />
          <SkeletonBar width="92%" height={14} />
          <SkeletonBar width="68%" height={14} />
        </div>
      </div>
    </Card>
  );

  return (
    <div className="admin-detail-stack">
      {statCards > 0 ? <AdminStatGridSkeleton count={statCards} showNotes={false} radius={12} /> : null}

      {sideSections > 0 ? (
        <Row gutter={[16, 16]} align="top">
          <Col xs={24} lg={15}>
            <div className="admin-detail-stack">
              {Array.from({ length: mainSections }).map((_, index) => renderSectionCard(`detail-main-${index}`))}
            </div>
          </Col>
          <Col xs={24} lg={9}>
            <div className="admin-detail-stack">
              {Array.from({ length: sideSections }).map((_, index) => renderSectionCard(`detail-side-${index}`))}
            </div>
          </Col>
        </Row>
      ) : (
        <div className="admin-detail-stack">
          {Array.from({ length: mainSections }).map((_, index) => renderSectionCard(`detail-only-${index}`))}
        </div>
      )}
    </div>
  );
}

export function AdminFormSplitSkeleton({
  mainCards = 2,
  sideCards = 2,
}: {
  mainCards?: number;
  sideCards?: number;
}) {
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={16}>
        <div className="space-y-4">
          {Array.from({ length: mainCards }).map((_, index) => (
            <Card key={`form-main-${index}`} bordered={false} className="admin-loading-card rounded-2xl shadow-sm">
              <div className="space-y-4">
                <div className="space-y-2">
                  <SkeletonBar width="28%" height={14} />
                  <SkeletonBar width="70%" height={12} />
                </div>
                <Row gutter={[16, 16]}>
                  {Array.from({ length: 4 }).map((__, fieldIndex) => (
                    <Col xs={24} md={12} key={`form-field-${index}-${fieldIndex}`}>
                      <div className="space-y-2">
                        <SkeletonBar width="32%" height={12} />
                        <SkeletonBar width="100%" height={40} radius={14} />
                      </div>
                    </Col>
                  ))}
                </Row>
                <div className="flex justify-end">
                  <SkeletonBar width={148} height={40} radius={14} />
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Col>
      <Col xs={24} lg={8}>
        <AdminCardStackSkeleton count={sideCards} />
      </Col>
    </Row>
  );
}

export function AdminHeatmapSkeleton({
  withSideCards = true,
  inverse = false,
  topStatPalettes,
}: {
  withSideCards?: boolean;
  inverse?: boolean;
  topStatPalettes?: AdminStatPalette[];
}) {
  const heatmapContent = (
    <Card
      bordered={false}
      className={`rounded-2xl shadow-sm${inverse ? " border border-white/10 bg-white/10" : " border border-gray-100 bg-white"}`}
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-2">
            <SkeletonBar width={164} height={14} inverse={inverse} />
            <SkeletonBar width={220} height={12} inverse={inverse} />
          </div>
          <SkeletonBar width={126} height={38} inverse={inverse} radius={14} />
        </div>

        <div className={`space-y-2 rounded-2xl border p-4${inverse ? " border-white/18 bg-white/10" : " border-gray-100 bg-gray-50/70"}`}>
          {Array.from({ length: 7 }).map((_, rowIndex) => (
            <div key={`heatmap-row-${rowIndex}`} className="flex items-center gap-2">
              <SkeletonBar width={58} height={10} inverse={inverse} />
              <div className="grid flex-1 gap-1" style={{ gridTemplateColumns: "repeat(24, minmax(0, 1fr))" }}>
                {Array.from({ length: 24 }).map((__, cellIndex) => (
                  <div
                    key={`heatmap-cell-${rowIndex}-${cellIndex}`}
                    className={`admin-skeleton-block${inverse ? " admin-skeleton-block-inverse" : ""}`}
                    style={{ height: 14, borderRadius: 6, opacity: 0.55 + ((rowIndex + cellIndex) % 4) * 0.08 }}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );

  if (!withSideCards) {
    return (
      <div className="space-y-4">
        {topStatPalettes && topStatPalettes.length > 0 ? (
          <AdminStatGridSkeleton cardPalettes={topStatPalettes} showNotes={false} colProps={{ xs: 24, sm: 12, xl: 6 }} />
        ) : null}
        {heatmapContent}
      </div>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} xl={16}>
        {heatmapContent}
      </Col>
      <Col xs={24} xl={8}>
        <div className="admin-card-stack">
          <AdminStatCardSkeleton
            palette={{ backgroundColor: "#eef2ff", borderColor: "#c7d2fe" }}
            className="flex flex-1 flex-col justify-center"
            titleWidth="48%"
            valueWidth="54%"
            noteWidth="82%"
            inverse={inverse}
          />
          <AdminStatCardSkeleton
            palette={{ backgroundColor: "#ecfdf5", borderColor: "#a7f3d0" }}
            className="flex flex-1 flex-col justify-center"
            titleWidth="46%"
            valueWidth="50%"
            noteWidth="88%"
            inverse={inverse}
          />
        </div>
      </Col>
    </Row>
  );
}
