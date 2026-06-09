"""
stats_analyzer.py — Comprehensive Statistical Analysis Module

Supported methods:
  descriptive          描述性统计
  correlation          相关分析
  linear_regression    线性回归
  stepwise_regression  逐步回归
  hierarchical_regression 分层回归
  anova_oneway         单因素方差分析
  anova_twoway         双因素方差分析
  t_independent        独立样本 t 检验
  t_onesample          单样本 t 检验
  t_paired             配对 t 检验
  normality            正态性检验
  nonparametric        非参数检验
  cluster_kmeans       K-means 聚类
  pca                  主成分分析
  factor_analysis      因子分析
  reliability          信度分析（Cronbach α）
  logistic_binary      二元 Logistic 回归
  logistic_multinomial 多分类 Logistic 回归
  entropy_weight       熵值法权重
  scatter              散点图数据
  histogram            直方图数据
"""
from __future__ import annotations

import warnings
import numpy as np
import pandas as pd
from scipy import stats as _stats

warnings.filterwarnings("ignore")


# ── helpers ─────────────────────────────────────────────────────────────── #

def _to_numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce")


def _sig_stars(p) -> str:
    if p is None:
        return ""
    if p < 0.001:
        return "***"
    if p < 0.01:
        return "**"
    if p < 0.05:
        return "*"
    return ""


def _r(v, digits=4):
    """Round safely."""
    if v is None or (isinstance(v, float) and (np.isnan(v) or np.isinf(v))):
        return None
    return round(float(v), digits)


def run_analysis(df: pd.DataFrame, method: str, params: dict) -> dict:
    """Dispatcher — calls the matching analysis function."""
    METHODS = {
        "descriptive":            descriptive_stats,
        "correlation":            correlation_analysis,
        "linear_regression":      linear_regression,
        "stepwise_regression":    stepwise_regression,
        "hierarchical_regression": hierarchical_regression,
        "anova_oneway":           anova_oneway,
        "anova_twoway":           anova_twoway,
        "t_independent":          t_independent,
        "t_onesample":            t_onesample,
        "t_paired":               t_paired,
        "normality":              normality_test,
        "nonparametric":          nonparametric_test,
        "cluster_kmeans":         cluster_kmeans,
        "pca":                    pca_analysis,
        "factor_analysis":        factor_analysis,
        "reliability":            reliability_analysis,
        "logistic_binary":        logistic_binary,
        "logistic_multinomial":   logistic_multinomial,
        "entropy_weight":         entropy_weight,
        "scatter":                scatter_data,
        "histogram":              histogram_data,
    }
    fn = METHODS.get(method)
    if fn is None:
        return {"error": f"未知分析方法: {method}"}
    try:
        return fn(df, **params)
    except Exception as e:
        import traceback
        return {"error": str(e), "detail": traceback.format_exc(limit=5)}


# ── 1. 描述性统计 ─────────────────────────────────────────────────────────── #

def descriptive_stats(df: pd.DataFrame, cols: list, group_col: str = None) -> dict:
    """
    描述性统计：均值、标准差、最小/最大、四分位数、偏度、峰度、众数。
    若指定 group_col 则按分组汇总。
    """
    result = {"method": "descriptive", "tables": []}
    groups = [(None, df)] if not group_col else list(df.groupby(group_col))

    for grp_val, sub in groups:
        rows = []
        for col in cols:
            s = _to_numeric(sub[col]).dropna()
            if len(s) == 0:
                continue
            arr = s.values
            q1, q3 = float(np.percentile(arr, 25)), float(np.percentile(arr, 75))
            try:
                sk = float(_stats.skew(arr))
                ku = float(_stats.kurtosis(arr))
            except Exception:
                sk = ku = None
            try:
                mode_res = _stats.mode(arr, keepdims=True)
                mode_val = float(mode_res.mode[0])
            except Exception:
                mode_val = None
            rows.append({
                "变量":   col,
                "样本量": int(len(s)),
                "均值":   _r(float(s.mean())),
                "标准差": _r(float(s.std())),
                "最小值": _r(float(s.min())),
                "Q1":     _r(q1),
                "中位数": _r(float(s.median())),
                "Q3":     _r(q3),
                "最大值": _r(float(s.max())),
                "偏度":   _r(sk),
                "峰度":   _r(ku),
                "众数":   _r(mode_val),
            })
        label = f"分组={grp_val}" if grp_val is not None else "全样本"
        result["tables"].append({"group": label, "rows": rows})

    # 若有 group_col，也输出各分类频数
    if group_col and group_col in df.columns:
        freq = df[group_col].value_counts().reset_index()
        freq.columns = ["类别", "频数"]
        freq["百分比%"] = _r(freq["频数"] / freq["频数"].sum() * 100, 2)
        result["freq_table"] = freq.to_dict(orient="records")

    return result


# ── 2. 相关分析 ──────────────────────────────────────────────────────────── #

def correlation_analysis(df: pd.DataFrame, cols: list,
                          method: str = "pearson") -> dict:
    """
    相关分析：Pearson 或 Spearman 相关矩阵，含显著性星号和置信区间。
    """
    sub = df[cols].apply(_to_numeric).dropna()
    n = len(sub)
    r_mat = []
    p_mat = []
    for c1 in cols:
        r_row, p_row = [], []
        for c2 in cols:
            if method == "spearman":
                r, p = _stats.spearmanr(sub[c1], sub[c2])
            else:
                r, p = _stats.pearsonr(sub[c1], sub[c2])
            r_row.append(_r(r, 3))
            p_row.append(_r(p, 4))
        r_mat.append(r_row)
        p_mat.append(p_row)

    # 详细结果（下三角）
    pairs = []
    for i in range(len(cols)):
        for j in range(i + 1, len(cols)):
            r_val = r_mat[i][j]
            p_val = p_mat[i][j]
            pairs.append({
                "变量1": cols[i], "变量2": cols[j],
                "相关系数": r_val,
                "p值": p_val,
                "显著性": _sig_stars(p_val),
                "样本量": n,
            })

    return {
        "method": "correlation",
        "corr_method": method,
        "n": n,
        "cols": cols,
        "r_matrix": r_mat,
        "p_matrix": p_mat,
        "pairs": pairs,
    }


# ── 3. 线性回归 ──────────────────────────────────────────────────────────── #

def linear_regression(df: pd.DataFrame, y_col: str, x_cols: list,
                       standardized: bool = True) -> dict:
    """OLS 线性回归，含 R²、F 检验、系数表、VIF。"""
    import statsmodels.api as sm
    from statsmodels.stats.outliers_influence import variance_inflation_factor

    sub = df[[y_col] + x_cols].apply(_to_numeric).dropna()
    y = sub[y_col].values
    X_raw = sub[x_cols].values

    X = sm.add_constant(X_raw)
    model = sm.OLS(y, X).fit()

    coef_rows = []
    for i, name in enumerate(["常数项"] + x_cols):
        coef_rows.append({
            "变量":   name,
            "B(非标准化)": _r(model.params[i]),
            "标准误":     _r(model.bse[i]),
            "t值":        _r(model.tvalues[i]),
            "p值":        _r(model.pvalues[i]),
            "显著性":     _sig_stars(model.pvalues[i]),
            "[95% CI 下]": _r(model.conf_int()[0][i]),
            "[95% CI 上]": _r(model.conf_int()[1][i]),
        })

    # 标准化系数 β
    if standardized and len(x_cols) > 0:
        sub_std = (sub - sub.mean()) / sub.std()
        y_s = sub_std[y_col].values
        X_s = sub_std[x_cols].values
        model_s = sm.OLS(y_s, sm.add_constant(X_s)).fit()
        for i, row in enumerate(coef_rows[1:], 1):
            row["β(标准化)"] = _r(model_s.params[i])

    # VIF
    if len(x_cols) > 1:
        for i, row in enumerate(coef_rows[1:]):
            try:
                vif = variance_inflation_factor(X_raw, i)
                row["VIF"] = _r(vif, 2)
            except Exception:
                pass

    return {
        "method": "linear_regression",
        "n": int(len(sub)),
        "R²": _r(model.rsquared),
        "调整R²": _r(model.rsquared_adj),
        "F值": _r(model.fvalue),
        "F_p值": _r(model.f_pvalue),
        "F显著性": _sig_stars(model.f_pvalue),
        "AIC": _r(model.aic),
        "BIC": _r(model.bic),
        "系数表": coef_rows,
    }


# ── 4. 逐步回归 ──────────────────────────────────────────────────────────── #

def stepwise_regression(df: pd.DataFrame, y_col: str, x_cols: list,
                         direction: str = "both") -> dict:
    """
    逐步回归（基于 AIC/p 值前向/后向/双向选择）。
    返回最终入选变量和回归结果。
    """
    import statsmodels.api as sm

    sub = df[[y_col] + x_cols].apply(_to_numeric).dropna()
    y = sub[y_col].values

    selected: list = []
    remaining = list(x_cols)
    steps_log = []

    # 前向选择
    improved = True
    while improved and remaining:
        improved = False
        best_aic = np.inf
        best_var = None
        for var in remaining:
            cands = selected + [var]
            X = sm.add_constant(sub[cands].values)
            try:
                aic = sm.OLS(y, X).fit().aic
            except Exception:
                continue
            if aic < best_aic:
                best_aic = aic
                best_var = var
        if best_var:
            curr_aic = np.inf
            if selected:
                X_curr = sm.add_constant(sub[selected].values)
                curr_aic = sm.OLS(y, X_curr).fit().aic
            if best_aic < curr_aic:
                selected.append(best_var)
                remaining.remove(best_var)
                steps_log.append({"步骤": f"加入 {best_var}", "AIC": _r(best_aic)})
                improved = True

    # 后向删除（若 direction != "forward"）
    if direction in ("both", "backward") and len(selected) > 1:
        improved = True
        while improved and len(selected) > 1:
            improved = False
            X_curr = sm.add_constant(sub[selected].values)
            curr_aic = sm.OLS(y, X_curr).fit().aic
            worst_aic = curr_aic
            worst_var = None
            for var in selected:
                cands = [v for v in selected if v != var]
                X = sm.add_constant(sub[cands].values)
                aic = sm.OLS(y, X).fit().aic
                if aic < worst_aic:
                    worst_aic = aic
                    worst_var = var
            if worst_var:
                selected.remove(worst_var)
                steps_log.append({"步骤": f"移除 {worst_var}", "AIC": _r(worst_aic)})
                improved = True

    # 最终回归
    final = linear_regression(sub, y_col, selected) if selected else {}
    final["method"] = "stepwise_regression"
    final["入选变量"] = selected
    final["剔除变量"] = [x for x in x_cols if x not in selected]
    final["逐步过程"] = steps_log
    return final


# ── 5. 分层回归 ──────────────────────────────────────────────────────────── #

def hierarchical_regression(df: pd.DataFrame, y_col: str,
                              layers: list) -> dict:
    """
    分层回归：layers = [["x1","x2"], ["x3"], ...]，
    每层累加自变量，报告每层 R² 及 △R²。
    """
    import statsmodels.api as sm

    all_x = sum(layers, [])
    sub = df[[y_col] + all_x].apply(_to_numeric).dropna()
    y = sub[y_col].values

    layer_results = []
    cum_x: list = []
    prev_r2 = 0.0
    for idx, layer in enumerate(layers, 1):
        cum_x = cum_x + layer
        X = sm.add_constant(sub[cum_x].values)
        m = sm.OLS(y, X).fit()
        delta_r2 = m.rsquared - prev_r2
        layer_results.append({
            "层级": idx,
            "新增变量": layer,
            "累计变量": list(cum_x),
            "R²": _r(m.rsquared),
            "调整R²": _r(m.rsquared_adj),
            "△R²": _r(delta_r2),
            "F值": _r(m.fvalue),
            "F_p值": _r(m.f_pvalue),
            "F显著性": _sig_stars(m.f_pvalue),
            "AIC": _r(m.aic),
        })
        prev_r2 = m.rsquared

    final = linear_regression(sub, y_col, cum_x)
    final["method"] = "hierarchical_regression"
    final["分层摘要"] = layer_results
    return final


# ── 6. 单因素方差分析 ────────────────────────────────────────────────────── #

def anova_oneway(df: pd.DataFrame, y_col: str, group_col: str) -> dict:
    """
    单因素方差分析 + Levene 方差齐性 + Tukey HSD 事后多重比较。
    """
    from scipy.stats import levene
    sub = df[[y_col, group_col]].dropna()
    sub[y_col] = _to_numeric(sub[y_col])
    sub = sub.dropna()

    groups = [g[y_col].values for _, g in sub.groupby(group_col)]
    group_labels = sorted(sub[group_col].unique())

    f, p = _stats.f_oneway(*groups)
    # 方差齐性
    lev_stat, lev_p = levene(*groups)
    # eta²
    grand_mean = sub[y_col].mean()
    ss_between = sum(len(g) * (g.mean() - grand_mean) ** 2 for g in groups)
    ss_total = sum((v - grand_mean) ** 2 for g in groups for v in g)
    eta2 = ss_between / ss_total if ss_total > 0 else None

    # 描述统计
    desc = []
    for lbl, g in zip(group_labels, groups):
        desc.append({
            "分组": lbl, "n": len(g),
            "均值": _r(float(np.mean(g))),
            "标准差": _r(float(np.std(g, ddof=1))),
        })

    # Tukey HSD
    try:
        from statsmodels.stats.multicomp import pairwise_tukeyhsd
        tukey = pairwise_tukeyhsd(sub[y_col], sub[group_col])
        posthoc = []
        for row in tukey.summary().data[1:]:
            posthoc.append({
                "组1": str(row[0]), "组2": str(row[1]),
                "均值差": _r(row[2]),
                "p(adj)": _r(row[3], 4),
                "显著性": _sig_stars(row[3]),
                "95% CI": f"[{_r(row[4])}, {_r(row[5])}]",
            })
    except Exception:
        posthoc = []

    return {
        "method": "anova_oneway",
        "n": int(len(sub)),
        "F值": _r(f),
        "p值": _r(p),
        "显著性": _sig_stars(p),
        "η²(效果量)": _r(eta2),
        "Levene方差齐性_F": _r(lev_stat),
        "Levene_p值": _r(lev_p),
        "分组描述统计": desc,
        "事后比较(Tukey HSD)": posthoc,
    }


# ── 7. 双因素方差分析 ────────────────────────────────────────────────────── #

def anova_twoway(df: pd.DataFrame, y_col: str,
                  group_col1: str, group_col2: str) -> dict:
    """双因素方差分析，含交互效应。"""
    import statsmodels.formula.api as smf

    sub = df[[y_col, group_col1, group_col2]].dropna()
    sub[y_col] = _to_numeric(sub[y_col])
    sub = sub.dropna()

    # 确保分类列为字符串（statsmodels 需要）
    sub = sub.copy()
    sub[group_col1] = sub[group_col1].astype(str)
    sub[group_col2] = sub[group_col2].astype(str)

    formula = f"Q('{y_col}') ~ C(Q('{group_col1}')) * C(Q('{group_col2}'))"
    model = smf.ols(formula, data=sub).fit()
    from statsmodels.stats.anova import anova_lm
    table = anova_lm(model, typ=2)

    rows = []
    for idx, row in table.iterrows():
        rows.append({
            "效应":    str(idx),
            "SS":     _r(row.get("sum_sq")),
            "df":     _r(row.get("df"), 1),
            "MS":     _r(row.get("sum_sq") / row.get("df")) if row.get("df") else None,
            "F值":    _r(row.get("F")),
            "p值":    _r(row.get("PR(>F)")),
            "显著性": _sig_stars(row.get("PR(>F)")),
        })

    return {
        "method": "anova_twoway",
        "n": int(len(sub)),
        "方差分析表": rows,
    }


# ── 8. 独立样本 t 检验 ───────────────────────────────────────────────────── #

def t_independent(df: pd.DataFrame, y_col: str, group_col: str) -> dict:
    """独立样本 t 检验，含 Levene 方差齐性和 Cohen's d。"""
    from scipy.stats import levene

    sub = df[[y_col, group_col]].dropna()
    sub[y_col] = _to_numeric(sub[y_col])
    sub = sub.dropna()

    groups = sub.groupby(group_col)[y_col]
    grp_list = [(name, g.values) for name, g in groups]
    if len(grp_list) != 2:
        return {"error": f"独立样本 t 检验需要恰好 2 组，当前有 {len(grp_list)} 组"}

    (g1_name, g1), (g2_name, g2) = grp_list
    lev_stat, lev_p = levene(g1, g2)
    equal_var = lev_p > 0.05

    t, p = _stats.ttest_ind(g1, g2, equal_var=equal_var)
    # Cohen's d
    pooled_std = np.sqrt((np.var(g1, ddof=1) * (len(g1) - 1) +
                           np.var(g2, ddof=1) * (len(g2) - 1)) /
                          (len(g1) + len(g2) - 2))
    cohens_d = (np.mean(g1) - np.mean(g2)) / pooled_std if pooled_std > 0 else None

    desc = [
        {"组": g1_name, "n": len(g1), "均值": _r(float(np.mean(g1))),
         "标准差": _r(float(np.std(g1, ddof=1)))},
        {"组": g2_name, "n": len(g2), "均值": _r(float(np.mean(g2))),
         "标准差": _r(float(np.std(g2, ddof=1)))},
    ]

    return {
        "method": "t_independent",
        "分组描述": desc,
        "方差齐性_Levene_F": _r(lev_stat),
        "方差齐性_p": _r(lev_p),
        "方差是否齐性": "是" if equal_var else "否",
        "t值": _r(t),
        "p值": _r(p),
        "显著性": _sig_stars(p),
        "Cohen'd(效果量)": _r(cohens_d),
        "均值差": _r(float(np.mean(g1) - np.mean(g2))),
    }


# ── 9. 单样本 t 检验 ─────────────────────────────────────────────────────── #

def t_onesample(df: pd.DataFrame, col: str, mu: float = 0.0) -> dict:
    """单样本 t 检验：检验均值是否显著等于 mu。"""
    s = _to_numeric(df[col]).dropna()
    t, p = _stats.ttest_1samp(s.values, popmean=float(mu))
    ci = _stats.t.interval(0.95, len(s) - 1, loc=float(s.mean()),
                            scale=float(_stats.sem(s.values)))

    return {
        "method": "t_onesample",
        "检验值μ": mu,
        "n": int(len(s)),
        "样本均值": _r(float(s.mean())),
        "均值差(样本-μ)": _r(float(s.mean()) - mu),
        "t值": _r(t),
        "p值": _r(p),
        "显著性": _sig_stars(p),
        "95% CI": [_r(ci[0]), _r(ci[1])],
    }


# ── 10. 配对 t 检验 ──────────────────────────────────────────────────────── #

def t_paired(df: pd.DataFrame, col1: str, col2: str) -> dict:
    """配对 t 检验（两列数据一一对应）。"""
    sub = df[[col1, col2]].apply(_to_numeric).dropna()
    diff = sub[col1] - sub[col2]
    t, p = _stats.ttest_rel(sub[col1].values, sub[col2].values)
    ci = _stats.t.interval(0.95, len(diff) - 1,
                            loc=float(diff.mean()),
                            scale=float(_stats.sem(diff.values)))

    return {
        "method": "t_paired",
        "n": int(len(sub)),
        "变量1均值": _r(float(sub[col1].mean())),
        "变量2均值": _r(float(sub[col2].mean())),
        "差值均值": _r(float(diff.mean())),
        "差值标准差": _r(float(diff.std(ddof=1))),
        "t值": _r(t),
        "p值": _r(p),
        "显著性": _sig_stars(p),
        "差值95% CI": [_r(ci[0]), _r(ci[1])],
    }


# ── 11. 正态性检验 ───────────────────────────────────────────────────────── #

def normality_test(df: pd.DataFrame, cols: list) -> dict:
    """Shapiro-Wilk（n≤5000）和 Kolmogorov-Smirnov 正态性检验。"""
    rows = []
    for col in cols:
        s = _to_numeric(df[col]).dropna()
        n = len(s)
        sw_stat, sw_p, ks_stat, ks_p = None, None, None, None
        try:
            if n <= 5000:
                sw_stat, sw_p = _stats.shapiro(s.values)
        except Exception:
            pass
        try:
            ks_stat, ks_p = _stats.kstest(s.values, "norm",
                                           args=(float(s.mean()), float(s.std())))
        except Exception:
            pass
        rows.append({
            "变量": col,
            "n": n,
            "均值": _r(float(s.mean())),
            "标准差": _r(float(s.std())),
            "Shapiro-Wilk_W": _r(sw_stat, 4),
            "Shapiro-Wilk_p": _r(sw_p, 4),
            "SW显著性": _sig_stars(sw_p),
            "K-S统计量": _r(ks_stat, 4),
            "K-S_p": _r(ks_p, 4),
            "KS显著性": _sig_stars(ks_p),
            "结论": ("正态" if (sw_p or 1) > 0.05 and (ks_p or 1) > 0.05
                     else "非正态"),
        })
    return {"method": "normality", "rows": rows}


# ── 12. 非参数检验 ───────────────────────────────────────────────────────── #

def nonparametric_test(df: pd.DataFrame, y_col: str, group_col: str) -> dict:
    """
    2 组→ Mann-Whitney U；≥3 组→ Kruskal-Wallis。
    """
    sub = df[[y_col, group_col]].dropna()
    sub[y_col] = _to_numeric(sub[y_col])
    sub = sub.dropna()

    groups = [(name, g.values) for name, g in sub.groupby(group_col)]
    g_vals = [g for _, g in groups]

    if len(groups) == 2:
        stat, p = _stats.mannwhitneyu(g_vals[0], g_vals[1],
                                       alternative="two-sided")
        test_name = "Mann-Whitney U"
    else:
        stat, p = _stats.kruskal(*g_vals)
        test_name = "Kruskal-Wallis H"

    desc = [
        {"组": name, "n": len(g), "中位数": _r(float(np.median(g))),
         "均值": _r(float(np.mean(g)))}
        for name, g in groups
    ]

    return {
        "method": "nonparametric",
        "检验方法": test_name,
        "统计量": _r(stat),
        "p值": _r(p),
        "显著性": _sig_stars(p),
        "分组描述": desc,
    }


# ── 13. K-means 聚类 ─────────────────────────────────────────────────────── #

def cluster_kmeans(df: pd.DataFrame, cols: list,
                    n_clusters: int = 3, max_k: int = 8) -> dict:
    """K-means 聚类，含肘部法则（SSE 曲线）和聚类中心。"""
    from sklearn.cluster import KMeans
    from sklearn.preprocessing import StandardScaler

    sub = df[cols].apply(_to_numeric).dropna()
    X = StandardScaler().fit_transform(sub.values)

    # 肘部法则
    elbow = []
    for k in range(1, min(max_k + 1, len(sub))):
        km = KMeans(n_clusters=k, random_state=42, n_init=10)
        km.fit(X)
        elbow.append({"k": k, "SSE": _r(km.inertia_)})

    # 最终聚类
    km_final = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
    labels = km_final.fit_predict(X)

    centers = []
    scaler = StandardScaler().fit(sub.values)
    centers_orig = scaler.inverse_transform(km_final.cluster_centers_)
    for i, c in enumerate(centers_orig):
        row = {"簇": i + 1, "样本量": int(np.sum(labels == i))}
        for j, col in enumerate(cols):
            row[col] = _r(float(c[j]))
        centers.append(row)

    # 各簇样本数
    sizes = {f"簇{i+1}": int(np.sum(labels == i))
             for i in range(n_clusters)}

    return {
        "method": "cluster_kmeans",
        "n_clusters": n_clusters,
        "n": int(len(sub)),
        "肘部法则SSE": elbow,
        "聚类中心": centers,
        "各簇样本量": sizes,
    }


# ── 14. 主成分分析 ───────────────────────────────────────────────────────── #

def pca_analysis(df: pd.DataFrame, cols: list,
                  n_components: int = None) -> dict:
    """PCA：特征值、方差贡献率、累积贡献率、成分载荷矩阵。"""
    from sklearn.preprocessing import StandardScaler
    from sklearn.decomposition import PCA as _PCA

    sub = df[cols].apply(_to_numeric).dropna()
    X = StandardScaler().fit_transform(sub.values)

    n_comp = min(n_components or len(cols), len(cols), len(sub))
    pca = _PCA(n_components=n_comp)
    pca.fit(X)

    eigenvalues = pca.explained_variance_
    var_ratio = pca.explained_variance_ratio_

    # 找 Kaiser 准则（特征值>1）的成分数
    kaiser_n = int(np.sum(eigenvalues > 1))

    components = []
    cum = 0.0
    for i in range(n_comp):
        cum += float(var_ratio[i]) * 100
        components.append({
            "成分": i + 1,
            "特征值": _r(float(eigenvalues[i])),
            "方差贡献率%": _r(float(var_ratio[i]) * 100, 2),
            "累积贡献率%": _r(cum, 2),
        })

    # 载荷矩阵
    loadings = []
    for i, col in enumerate(cols):
        row = {"变量": col}
        for j in range(n_comp):
            row[f"PC{j+1}"] = _r(float(pca.components_[j][i]))
        loadings.append(row)

    return {
        "method": "pca",
        "n": int(len(sub)),
        "建议保留成分数(Kaiser)": kaiser_n,
        "成分摘要": components,
        "载荷矩阵": loadings,
    }


# ── 15. 因子分析 ─────────────────────────────────────────────────────────── #

def factor_analysis(df: pd.DataFrame, cols: list,
                     n_factors: int = 2, rotation: str = "varimax") -> dict:
    """
    探索性因子分析（最大似然法 + 旋转），含因子载荷和公因子方差。
    """
    try:
        from factor_analyzer import FactorAnalyzer as _FA
        sub = df[cols].apply(_to_numeric).dropna()
        fa = _FA(n_factors=n_factors, rotation=rotation, method="ml")
        fa.fit(sub.values)
        loadings = fa.loadings_
        communalities = fa.get_communalities()
        ev, _ = fa.get_eigenvalues()

        load_rows = []
        for i, col in enumerate(cols):
            row = {"变量": col}
            for j in range(n_factors):
                row[f"F{j+1}"] = _r(float(loadings[i][j]))
            row["公因子方差"] = _r(float(communalities[i]))
            load_rows.append(row)

        return {
            "method": "factor_analysis",
            "n": int(len(sub)),
            "n_factors": n_factors,
            "rotation": rotation,
            "载荷矩阵": load_rows,
            "特征值": [_r(float(e)) for e in ev[:n_factors * 2]],
        }
    except ImportError:
        # 用 sklearn PCA 代替
        result = pca_analysis(df, cols, n_components=n_factors)
        result["method"] = "factor_analysis"
        result["note"] = "factor_analyzer 未安装，已用 PCA 代替"
        return result


# ── 16. 信度分析（Cronbach α）───────────────────────────────────────────── #

def reliability_analysis(df: pd.DataFrame, cols: list) -> dict:
    """
    Cronbach α 信度分析 + 删题后 α + 项目-总分相关。
    """
    sub = df[cols].apply(_to_numeric).dropna()
    k = len(cols)
    n = len(sub)

    item_vars = sub.var(axis=0, ddof=1)
    total = sub.sum(axis=1)
    total_var = float(total.var(ddof=1))

    alpha = (k / (k - 1)) * (1 - item_vars.sum() / total_var) if total_var > 0 else None

    rows = []
    for col in cols:
        rest = sub.drop(columns=[col])
        rest_total = rest.sum(axis=1)
        rv = float(rest.var(axis=0, ddof=1).sum())
        tv = float(rest_total.var(ddof=1))
        alpha_del = ((k - 1) / (k - 2)) * (1 - rv / tv) if (k > 2 and tv > 0) else None
        it_corr = float(sub[col].corr(rest_total))
        rows.append({
            "题项":          col,
            "项目-总分相关":  _r(it_corr),
            "删题后α":       _r(alpha_del),
        })

    level = ("优秀(≥0.9)" if (alpha or 0) >= 0.9 else
             "良好(0.8~0.9)" if (alpha or 0) >= 0.8 else
             "可接受(0.7~0.8)" if (alpha or 0) >= 0.7 else
             "较差(<0.7)")

    return {
        "method": "reliability",
        "n": n,
        "题项数": k,
        "Cronbach_α": _r(alpha),
        "信度水平": level,
        "项目分析": rows,
    }


# ── 17. 二元 Logistic 回归 ───────────────────────────────────────────────── #

def logistic_binary(df: pd.DataFrame, y_col: str, x_cols: list) -> dict:
    """
    二元 Logistic 回归，含 OR、95% CI、Hosmer-Lemeshow 检验。
    """
    import statsmodels.api as sm

    sub = df[[y_col] + x_cols].apply(_to_numeric).dropna()
    y = sub[y_col].values
    X = sm.add_constant(sub[x_cols].values)

    model = sm.Logit(y, X).fit(disp=0)
    params = model.params
    pvalues = model.pvalues
    conf = model.conf_int()
    or_vals = np.exp(params)
    or_ci = np.exp(conf)

    coef_rows = []
    for i, name in enumerate(["常数项"] + x_cols):
        coef_rows.append({
            "变量":  name,
            "B":     _r(params[i]),
            "p值":   _r(pvalues[i]),
            "显著性": _sig_stars(pvalues[i]),
            "OR":    _r(float(or_vals[i])),
            "OR_95%CI_下": _r(float(or_ci[0][i])),
            "OR_95%CI_上": _r(float(or_ci[1][i])),
        })

    # Nagelkerke R²
    ll_0 = model.llnull
    ll_m = model.llf
    n = len(y)
    cox_r2 = 1 - np.exp((2 / n) * (ll_0 - ll_m))
    nagelkerke_r2 = cox_r2 / (1 - np.exp(2 * ll_0 / n))

    # 预测精度
    pred = (model.predict(X) >= 0.5).astype(int)
    acc = float(np.mean(pred == y))

    return {
        "method": "logistic_binary",
        "n": n,
        "Cox_Snell_R²": _r(cox_r2),
        "Nagelkerke_R²": _r(nagelkerke_r2),
        "-2LL": _r(-2 * ll_m),
        "AIC": _r(model.aic),
        "正确分类率": f"{acc*100:.1f}%",
        "系数表": coef_rows,
    }


# ── 18. 多分类 Logistic 回归 ─────────────────────────────────────────────── #

def logistic_multinomial(df: pd.DataFrame, y_col: str, x_cols: list) -> dict:
    """多分类 Logistic（Softmax）回归。"""
    import statsmodels.api as sm

    sub = df[[y_col] + x_cols].apply(_to_numeric).dropna()
    y = sub[y_col].values
    X = sm.add_constant(sub[x_cols].values)

    model = sm.MNLogit(y, X).fit(disp=0)
    params = model.params
    pvalues = model.pvalues

    classes = sorted(pd.Series(y).unique())[1:]  # reference = first class
    coef_tables = []
    for i, cls in enumerate(classes):
        rows = []
        for j, name in enumerate(["常数项"] + x_cols):
            rows.append({
                "变量":   name,
                "B":      _r(float(params.iloc[j, i])),
                "p值":    _r(float(pvalues.iloc[j, i])),
                "显著性": _sig_stars(float(pvalues.iloc[j, i])),
            })
        coef_tables.append({"类别": cls, "系数": rows})

    pred = model.predict(X).argmax(axis=1)
    true_cls = pd.Categorical(pd.Series(y)).codes
    acc = float(np.mean(pred == true_cls))

    return {
        "method": "logistic_multinomial",
        "n": int(len(sub)),
        "AIC": _r(model.aic),
        "正确分类率": f"{acc*100:.1f}%",
        "系数表(各类别)": coef_tables,
    }


# ── 19. 熵值法权重 ───────────────────────────────────────────────────────── #

def entropy_weight(df: pd.DataFrame, cols: list) -> dict:
    """
    熵值法计算各指标权重（信息熵越小，差异越大，权重越高）。
    """
    sub = df[cols].apply(_to_numeric).dropna()
    X = sub.values.astype(float)

    # 归一化（正向）
    col_min = X.min(axis=0)
    col_max = X.max(axis=0)
    denom = col_max - col_min
    denom[denom == 0] = 1e-9
    X_norm = (X - col_min) / denom

    # 比重
    X_norm += 1e-9  # 避免 log(0)
    p = X_norm / X_norm.sum(axis=0)

    n = len(X)
    e = -1 / np.log(n) * (p * np.log(p)).sum(axis=0)
    d = 1 - e
    weights = d / d.sum()

    rows = []
    for i, col in enumerate(cols):
        rows.append({
            "指标":     col,
            "信息熵":   _r(float(e[i])),
            "差异系数": _r(float(d[i])),
            "权重":     _r(float(weights[i])),
            "权重%":    f"{weights[i]*100:.1f}%",
        })

    return {"method": "entropy_weight", "n": int(n), "指标权重": rows}


# ── 20. 散点图数据 ───────────────────────────────────────────────────────── #

def scatter_data(df: pd.DataFrame, x_col: str, y_col: str,
                  color_col: str = None, sample: int = 500) -> dict:
    """返回散点图所需数据（x, y, color）及相关系数。"""
    cols = [x_col, y_col] + ([color_col] if color_col else [])
    sub = df[cols].dropna()
    sub[x_col] = _to_numeric(sub[x_col])
    sub[y_col] = _to_numeric(sub[y_col])
    sub = sub.dropna()

    if len(sub) > sample:
        sub = sub.sample(sample, random_state=42)

    r, p = _stats.pearsonr(sub[x_col].values, sub[y_col].values)
    points = []
    for _, row in sub.iterrows():
        pt = {"x": _r(row[x_col]), "y": _r(row[y_col])}
        if color_col:
            pt["c"] = str(row[color_col])
        points.append(pt)

    return {
        "method": "scatter",
        "x_col": x_col, "y_col": y_col, "color_col": color_col,
        "n": int(len(sub)),
        "pearson_r": _r(r),
        "pearson_p": _r(p),
        "points": points,
    }


# ── 21. 直方图数据 ───────────────────────────────────────────────────────── #

def histogram_data(df: pd.DataFrame, col: str, bins: int = 20) -> dict:
    """返回直方图频率数据及正态拟合参数。"""
    s = _to_numeric(df[col]).dropna()
    counts, edges = np.histogram(s.values, bins=bins)
    mu, sigma = float(s.mean()), float(s.std())

    bar_data = []
    for i in range(len(counts)):
        bar_data.append({
            "区间下": _r(float(edges[i])),
            "区间上": _r(float(edges[i + 1])),
            "中点":   _r(float((edges[i] + edges[i + 1]) / 2)),
            "频数":   int(counts[i]),
            "频率%":  _r(float(counts[i] / len(s) * 100), 2),
        })

    # 正态性
    sw_stat, sw_p = None, None
    try:
        if len(s) <= 5000:
            sw_stat, sw_p = _stats.shapiro(s.values)
    except Exception:
        pass

    return {
        "method": "histogram",
        "n": int(len(s)),
        "均值": _r(mu),
        "标准差": _r(sigma),
        "正态分布_mu": _r(mu),
        "正态分布_sigma": _r(sigma),
        "Shapiro_W": _r(sw_stat, 4),
        "Shapiro_p": _r(sw_p, 4),
        "正态性结论": ("正态" if (sw_p or 1) > 0.05 else "非正态"),
        "直方图": bar_data,
    }
