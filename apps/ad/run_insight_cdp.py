"""
CDP 自动化脚本：通过 Playwright 在浏览器中运行 Insight 分析
问题：为什么3月15号NSS下降了？
"""
import time
import os
from playwright.sync_api import sync_playwright

QUESTION = "为什么3月15号门店销售额下降了？"
BASE_URL  = "http://localhost:8080"
SCREENSHOT_DIR = "/tmp/insight_screenshots"

os.makedirs(SCREENSHOT_DIR, exist_ok=True)

def run():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=False,
            args=["--no-sandbox", "--disable-dev-shm-usage"]
        )
        page = browser.new_page(viewport={"width": 1440, "height": 900})

        # 阻断外部字体加载，避免截图超时
        page.route("**/*", lambda route: route.abort()
            if route.request.resource_type == "font"
            else route.continue_())

        # ── 1. 打开页面 ──────────────────────────────────────────────────── #
        print("▶ 打开 KG Builder 页面...")
        page.goto(BASE_URL, wait_until="domcontentloaded", timeout=30000)
        page.screenshot(path=f"{SCREENSHOT_DIR}/01_home.png")
        print("  ✓ 页面加载完成")

        # ── 2. 切换到 Insight Tab ─────────────────────────────────────────── #
        print("▶ 切换到 Insight Tab...")
        # 尝试多种 selector
        tab_clicked = False
        for sel in ['a[href="#tab-insight"]', '#insight-tab', 'a:has-text("Insight")']:
            try:
                page.click(sel, timeout=3000)
                tab_clicked = True
                print(f"  ✓ 点击 Tab: {sel}")
                break
            except Exception:
                pass
        time.sleep(1)
        page.screenshot(path=f"{SCREENSHOT_DIR}/02_insight_tab.png")
        print("  ✓ Insight Tab 截图已保存")

        # ── 3. 输入问题 ───────────────────────────────────────────────────── #
        print(f"▶ 输入问题：{QUESTION}")
        # 尝试多种 selector
        filled = False
        for sel in ["#insight-question", "textarea[placeholder*='问题']", "textarea"]:
            try:
                el = page.locator(sel).first
                if el.count() > 0:
                    el.fill(QUESTION)
                    filled = True
                    print(f"  ✓ 问题已填入（selector: {sel}）")
                    break
            except Exception:
                pass
        page.screenshot(path=f"{SCREENSHOT_DIR}/03_question_filled.png")

        # ── 4. 点击开始分析 ───────────────────────────────────────────────── #
        print("▶ 点击「开始 Insight 分析」...")
        page.click("#insight-start-btn")
        time.sleep(1)
        page.screenshot(path=f"{SCREENSHOT_DIR}/04_analysis_started.png")
        print("  ✓ 分析已启动，等待结果...")

        # ── 5. 监控分析进度（最多等待 8 分钟）──────────────────────────────── #
        start_time = time.time()
        last_log_count = 0
        parts_seen = set()
        done = False

        for i in range(160):  # 最多等 160 * 3 = 480秒 (8分钟)
            time.sleep(3)
            elapsed = int(time.time() - start_time)

            # 检查日志内容
            log_el = page.locator("#insight-log")
            log_text = log_el.inner_text() if log_el.count() > 0 else ""
            log_lines = [l for l in log_text.split("\n") if l.strip()]

            if len(log_lines) != last_log_count:
                # 打印新增日志行
                new_lines = log_lines[last_log_count:]
                last_log_count = len(log_lines)
                for line in new_lines:
                    if line.strip():
                        print(f"  [{elapsed}s] {line.strip()[:120]}")

            # 检查各 Part 是否出现（从日志中检测）
            part_keywords = {
                "Part1": ["part 1", "part1", "波动识别"],
                "Part2": ["part 2", "part2", "统计量化"],
                "Part3": ["part 3", "part3", "结构贡献"],
                "Part4": ["part 4", "part4", "kg", "图谱"],
                "Part5": ["part 5", "part5", "下钻", "归因"],
                "综合报告": ["综合报告", "report", "part 6", "part6"],
                "直接回答": ["insight 直接回答", "生成直接回答", "insight_start"],
            }
            log_lower = log_text.lower()
            for part_name, keywords in part_keywords.items():
                if part_name not in parts_seen and any(kw in log_lower for kw in keywords):
                    parts_seen.add(part_name)
                    print(f"  ✓ [{elapsed}s] {part_name} 出现")
                    page.screenshot(path=f"{SCREENSHOT_DIR}/part_{part_name}.png")

            # 检查 insight-answer-card 是否可见（JS 设置 display='' 后才可见）
            # 用 JS 直接检查 display 样式，绕过 Playwright is_visible() 的局限
            answer_visible = page.evaluate("""
                () => {
                    const el = document.getElementById('insight-answer-card');
                    if (!el) return false;
                    return el.style.display !== 'none' && el.style.display !== '';
                }
            """)
            # 也检查 card display 是空字符串（JS 设 display='' 表示显示）
            answer_display = page.evaluate("""
                () => {
                    const el = document.getElementById('insight-answer-card');
                    return el ? el.style.display : 'missing';
                }
            """)

            # 检查 answer-content 是否有实质内容
            answer_content = page.evaluate("""
                () => {
                    const el = document.getElementById('insight-answer-content');
                    return el ? el.innerText : '';
                }
            """)

            if answer_display != "none" and len((answer_content or "").strip()) > 100:
                done = True
                print(f"\n  ✅ [{elapsed}s] Insight 直接回答生成完成！(display={answer_display}, len={len(answer_content)})")
                break

            # 检查按钮是否恢复（分析结束的可靠信号）
            btn_disabled = page.evaluate("""
                () => {
                    const el = document.getElementById('insight-start-btn');
                    return el ? el.disabled : true;
                }
            """)
            if not btn_disabled and elapsed > 30:
                print(f"  ✓ [{elapsed}s] 分析流程已结束（按钮恢复可用）")
                # 再等3秒让流式内容渲染完
                time.sleep(3)
                done = True
                break

            # 检查是否出现明显错误
            error_el = page.locator("#insight-error-card, .alert-danger")
            for idx in range(error_el.count()):
                el = error_el.nth(idx)
                if el.is_visible():
                    error_text = el.inner_text()
                    print(f"\n  ❌ [{elapsed}s] 分析出错: {error_text[:200]}")

        # ── 6. 截图：全页 + 各分析区块 ────────────────────────────────────── #
        print("\n▶ 截图保存分析结果...")
        page.screenshot(path=f"{SCREENSHOT_DIR}/05_final_full.png", full_page=True)
        print(f"  全页截图: {SCREENSHOT_DIR}/05_final_full.png")

        # 输出 Insight 直接回答内容
        answer_text = page.evaluate("""
            () => {
                const el = document.getElementById('insight-answer-content');
                return el ? el.innerText : '';
            }
        """)
        if answer_text and len(answer_text.strip()) > 50:
            # 截图
            page.evaluate("document.getElementById('insight-answer-card').scrollIntoView()")
            time.sleep(0.5)
            page.screenshot(path=f"{SCREENSHOT_DIR}/06_insight_answer.png")
            print(f"\n{'='*60}")
            print("INSIGHT 直接回答:")
            print('='*60)
            print(answer_text[:3000])
            print('='*60)
        else:
            print(f"  ⚠ 未获取到 Insight 直接回答 (display={answer_display})")
            # 打印 innerHTML 调试
            html = page.evaluate("document.getElementById('insight-answer-card')?.outerHTML?.slice(0,300)")
            print(f"  DEBUG answer-card HTML: {html}")

        # 输出最后30行日志
        log_el = page.locator("#insight-log")
        if log_el.count() > 0:
            log_text = log_el.inner_text()
            last_lines = [l for l in log_text.split("\n") if l.strip()][-30:]
            print("\n最后30行日志:")
            for line in last_lines:
                print(f"  {line}")
            # 截图
            log_el.screenshot(path=f"{SCREENSHOT_DIR}/07_insight_log.png")

        # ── 7. 专项：验证 Part5 下钻 & 京津战区数据 ─────────────────────── #
        print("\n▶ 查找 Part5 下钻分析区块...")
        part5_candidates = [
            "[id*='part5']",
            "[id*='ins-part5']",
            "[id*='ana-part5']",
            ".drill-result",
            ".part5",
        ]
        for sel in part5_candidates:
            els = page.locator(sel)
            if els.count() > 0:
                try:
                    first = els.first
                    first.scroll_into_view_if_needed()
                    time.sleep(0.3)
                    first.screenshot(path=f"{SCREENSHOT_DIR}/08_part5_drill.png")
                    drill_text = first.inner_text()
                    print(f"  ✓ Part5 截图 ({sel}), 内容长度: {len(drill_text)}")
                    if drill_text.strip():
                        print(f"  Part5 内容片段: {drill_text[:500]}")
                    break
                except Exception as e:
                    print(f"  ⚠ {sel} 截图失败: {e}")

        # 检查日志中是否有京津战区相关行
        if log_text:
            jj_lines = [l for l in log_text.split("\n") if "京津" in l or "重庆" in l]
            if jj_lines:
                print(f"\n▶ 日志中京津/重庆相关行:")
                for line in jj_lines:
                    print(f"  {line}")

        browser.close()
        print(f"\n✅ 所有截图保存至: {SCREENSHOT_DIR}/")
        return done

if __name__ == "__main__":
    success = run()
    print(f"\n{'✅ 分析成功完成' if success else '⚠ 分析未完全完成，请检查截图'}")
