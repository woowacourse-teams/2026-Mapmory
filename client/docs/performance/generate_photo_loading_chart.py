#!/usr/bin/env python3
"""Generate a dependency-free SVG trend chart from photo-loading results."""

from __future__ import annotations

import argparse
import csv
import html
import math
from collections import defaultdict
from pathlib import Path


METRICS = (
    ("recommend_total_ms", "사진 추천 전체 시간"),
    ("pick_total_ms", "사진 추가 전체 시간"),
)


def read_results(path: Path) -> dict[str, list[dict[str, str | float]]]:
    grouped: dict[str, list[dict[str, str | float]]] = defaultdict(list)
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row.get("metric") not in {metric for metric, _ in METRICS}:
                continue
            try:
                value_ms = float(row["value_ms"])
            except (KeyError, TypeError, ValueError):
                continue
            row["value_ms"] = value_ms
            grouped[row["metric"]].append(row)
    return grouped


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def rounded_max(value: float) -> int:
    step = 50 if value <= 500 else 100
    return max(step, math.ceil(value / step) * step)


def text(x: float, y: float, value: object, size: int, fill: str, anchor: str = "start") -> str:
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}" '
        f'text-anchor="{anchor}">{esc(value)}</text>'
    )


def panel(metric: str, title: str, rows: list[dict[str, str | float]], top: int) -> str:
    width = 820
    left = 82
    right = 34
    plot_top = top + 46
    plot_bottom = top + 222
    plot_width = width - left - right
    plot_height = plot_bottom - plot_top
    maximum = rounded_max(max(float(row["value_ms"]) for row in rows))
    color = "#21E69A" if metric == "recommend_total_ms" else "#8DEDC5"
    output = [text(left, top + 18, title, 18, "#F4FFF9")]

    for tick in range(0, maximum + 1, maximum // 4):
        y = plot_bottom - (tick / maximum) * plot_height
        output.append(
            f'<line x1="{left}" y1="{y:.1f}" x2="{width - right}" y2="{y:.1f}" '
            'stroke="#27423C" stroke-width="1" />'
        )
        output.append(text(left - 10, y + 4, f"{tick}ms", 11, "#8EA9A0", "end"))

    points: list[tuple[float, float, dict[str, str | float]]] = []
    denominator = max(len(rows) - 1, 1)
    for index, row in enumerate(rows):
        x = left + (index / denominator) * plot_width
        y = plot_bottom - (float(row["value_ms"]) / maximum) * plot_height
        points.append((x, y, row))

    if len(points) > 1:
        path = " ".join(f"{x:.1f},{y:.1f}" for x, y, _ in points)
        output.append(f'<polyline points="{path}" fill="none" stroke="{color}" stroke-width="3" />')

    for x, y, row in points:
        label = f'{row["app_version"]} #{row["run"]}'
        output.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="{color}" />')
        output.append(text(x, y - 12, f'{float(row["value_ms"]):g}ms', 12, "#F4FFF9", "middle"))
        output.append(text(x, plot_bottom + 24, label, 11, "#8EA9A0", "middle"))
        output.append(text(x, plot_bottom + 42, row["cache_state"], 10, "#6E8C82", "middle"))

    return "\n".join(output)


def build_svg(grouped: dict[str, list[dict[str, str | float]]]) -> str:
    width = 820
    height = 590
    sections = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 590" role="img" '
        'aria-labelledby="title description">',
        '<title id="title">Mapmory 사진 로딩 성능 추세</title>',
        '<desc id="description">사진 추천과 사진 추가 흐름의 실행 시간 측정 결과</desc>',
        '<rect width="100%" height="100%" rx="20" fill="#0A171A" />',
        text(40, 46, "Mapmory 사진 로딩 성능 추세", 24, "#F4FFF9"),
        text(40, 73, "동일 기기 기준 초기 측정값 · Cold/Warm 조건을 구분", 13, "#8EA9A0"),
    ]
    top = 105
    for metric, title in METRICS:
        rows = grouped.get(metric, [])
        if rows:
            sections.append(panel(metric, title, rows, top))
            top += 260
    sections.append(text(40, height - 22, "단위: ms · 현재 데이터는 성능 개선의 인과관계를 증명하지 않음", 11, "#6E8C82"))
    sections.append("</svg>")
    return "\n".join(sections)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("photo-loading-results.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("photo-loading-trend.svg"))
    args = parser.parse_args()
    grouped = read_results(args.input)
    if not grouped:
        raise SystemExit("그래프로 만들 수 있는 측정 결과가 없습니다.")
    args.output.write_text(build_svg(grouped), encoding="utf-8")
    print(f"generated: {args.output}")


if __name__ == "__main__":
    main()
