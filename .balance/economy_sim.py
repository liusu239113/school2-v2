# -*- coding: utf-8 -*-
"""校长2 大学时代 经济循环模拟（单位：万元/月）
参数与 GameBalanceConfig/GameEngine 保持一致；多收取中性值(营销/压力/设施等=1.0)。
"""

TUITION_BASE = 0.45
TUITION_MULT = {1:1.0, 2:1.3, 3:1.7, 4:2.3, 5:3.0, 6:4.0}
RENT         = {1:0.5, 2:3.0, 3:15.0, 4:80.0, 5:500.0, 6:2000.0}
STU_OP       = {1:0.08, 2:0.12, 3:0.18, 4:0.28, 5:0.40, 6:0.55}
UPGRADE      = {2:80.0, 3:300.0, 4:1000.0, 5:15000.0, 6:80000.0}
UPGRADE_REP  = {2:200, 3:800, 4:2500, 5:8000, 6:30000}
UPGRADE_STU  = {2:120, 3:500, 4:1200, 5:3000, 6:8000}
SALARY = {"C":0.5, "B":1.2, "A":2.5, "S":5.0}
HIRE   = {"C":5.0, "B":20.0, "A":60.0, "S":200.0}
COLLEGE_FOUND = {"人文":18.0, "理学":36.0, "工学":58.0, "商学":72.0}
COLLEGE_MONTH = {"人文":1.2, "理学":2.4, "工学":3.6, "商学":4.2}
BUDGET_DEFAULT = 10 * 0.8  # 政策页默认点满10点

REP_BASE = {35:0.8, 100:0.9, 500:1.0, 2000:1.1, 5000:1.3, 10000:1.5}

def rep_factor(rep):
    f = 0.8
    for th, v in sorted(REP_BASE.items()):
        if rep >= th: f = v
    return f

def scenario(name, years, path):
    """path: list of dict(year=, action=...) 决策脚本"""
    cash, rep, level = 500.0, 35, 1
    teachers = {"C":6, "B":0, "A":0, "S":0}
    students = 0          # 在读（3个年级稳态≈3×年招生）
    colleges = []
    budget = BUDGET_DEFAULT
    intake_last = 0
    rows = []
    for year in range(1, years+1):
        # ---- 年初执行玩家决策 ----
        for act in [a for a in path if a.get("year")==year]:
            k = act["action"]
            if k.startswith("hire:"):
                lvl, n = k.split(":")[1], int(k.split(":")[2])
                cost = HIRE[lvl]*n
                if cash >= cost:
                    cash -= cost; teachers[lvl] += n
            elif k.startswith("found:"):
                c = k.split(":")[1]
                if c not in colleges and cash >= COLLEGE_FOUND[c]:
                    cash -= COLLEGE_FOUND[c]; colleges.append(c)
            elif k == "upgrade":
                tgt = level+1
                ok = (cash >= UPGRADE.get(tgt, 1e18) and rep >= UPGRADE_REP.get(tgt, 1e9)
                      and students >= UPGRADE_STU.get(tgt, 1e9))
                if ok:
                    cash -= UPGRADE[tgt]; level = tgt
        # ---- 9月招生 ----
        base = 100 * rep_factor(rep)  # avgClassSize40 x2.5
        intake = int(base * (1.0 + 0.02*len(colleges)))  # 学院小幅加成
        intake_last = intake
        students = min(students + intake, intake*3 + 60)  # 3年级稳态 + 低保底
        rep += intake // 10  # 招生带来声誉
        # ---- 月度结算 x12 ----
        t_mult = TUITION_MULT[level]
        salary_m = sum(SALARY[l]*n for l, n in teachers.items())
        # 年薪通胀简化：每过1年，教师薪资x1.03（用工资档位近似）
        infl = 1.03 ** (year-1)
        for l in teachers: SALARY[l] = {"C":0.5,"B":1.2,"A":2.5,"S":5.0}[l] * infl
        salary_m = sum(SALARY[l]*n for l, n in teachers.items())
        college_m = sum(COLLEGE_MONTH[c] for c in colleges)
        for y in range(year):
            pass
        rows.append({
            "year": year, "level": level, "intake": intake, "students": students,
            "tuition": round(students*TUITION_BASE*t_mult, 1),
            "salary": round(salary_m, 1), "rent": RENT[level],
            "stu_op": round(students*STU_OP[level], 1),
            "college": round(college_m, 1), "budget": budget,
            "net": round(students*TUITION_BASE*t_mult - salary_m - RENT[level]
                         - students*STU_OP[level] - college_m - budget, 1),
            "cash": round(cash, 1), "rep": rep,
        })
        cash += rows[-1]["net"]
        rep += 5  # 常规声誉增长
    print(f"\n===== {name} =====")
    print("年 级 招生 在读   学费   薪资    租金  生均   学院  预算   净利   现金   声望")
    for r in rows:
        print(f"{r['year']:>2} {r['level']} {r['intake']:>4} {r['students']:>5} "
              f"{r['tuition']:>6} {r['salary']:>6} {r['rent']:>6} {r['stu_op']:>5} "
              f"{r['college']:>5} {r['budget']:>5} {r['net']:>7} {r['cash']:>8} {r['rep']:>6}")
    return rows

# 场景A：稳健流（前期省钱，攒钱升级）
scenario("A 稳健流（不建学院快速升级）", 8, [
    {"year":2, "action":"hire:C:4"},
    {"year":3, "action":"upgrade"},
    {"year":4, "action":"hire:B:6"},
    {"year":4, "action":"upgrade"},
    {"year":6, "action":"hire:B:10"},
    {"year":6, "action":"hire:A:4"},
    {"year":6, "action":"upgrade"},
])

# 场景B：学院流（建满4学院，走质量线）
scenario("B 学院流（4学院全建）", 8, [
    {"year":1, "action":"found:人文"},
    {"year":2, "action":"hire:C:4"},
    {"year":2, "action":"found:理学"},
    {"year":3, "action":"upgrade"},
    {"year":4, "action":"found:工学"},
    {"year":4, "action":"hire:B:6"},
    {"year":5, "action":"upgrade"},
    {"year":6, "action":"found:商学"},
    {"year":7, "action":"hire:B:10"},
])

# 场景C：躺平流（什么都不做，检验现金流是否自锁/空转）
scenario("C 躺平流（不投入）", 8, [])
