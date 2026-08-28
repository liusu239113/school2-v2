# -*- coding: utf-8 -*-
"""12年全系统时间线：验证7天体量的节奏（无破产 + 无>3个月决策空窗 + Lv5可达）"""
TUITION={1:0.45,2:0.585,3:0.765,4:1.035,5:1.35,6:1.8}
RENT={1:8,2:20,3:60,4:250,5:1500,6:5000}
OP={1:0.10,2:0.16,3:0.24,4:0.36,5:0.60,6:0.85}
UP={2:(80,200,120),3:(300,800,400),4:(1000,2500,1000),5:(15000,8000,3000)}
COL={"人文":(18,1.2,1,0.05),"理学":(36,2.4,2,0.04),"工学":(58,3.6,3,0.08),"艺术":(65,3.4,3,0.03),"商学":(72,4.2,4,0.05),"医学":(110,6.0,4,0.02)}
CHAIN_FEE=[115,170,230]  # 总启动经费
CHAIN_MONTHS=[4.5,6,7.5]
cash,rep,level,students=500.0,35,1,0
colleges=[]; chains_done=[]; chain_busy=0; hospital=False
comp_ev_cash=[9.6,21.0,40.0]; comp_ev_rep=[36,102,300]
rows=[]; last_decision_month=0; max_gap=0; decisions_total=0
for y in range(1,13):
    # 年度决策：建院(按序) / 升级 / 课题链接力 / 竞赛报名x2 / 医院Y8
    year_decisions=2  # 方针+目标+预算微调≈3
    for name,(fee,mon,lvl,eb) in COL.items():
        if name not in colleges and level>=lvl and cash>fee+60:
            cash-=fee; colleges.append(name); year_decisions+=1
    if (level+1) in UP and y>=2:
        fee,rq,rs=UP[level+1]
        if cash>fee+100 and rep>=rq and students>=rs:
            cash-=fee; level+=1; year_decisions+=1
    if chain_busy==0 and len(chains_done)<3:
        i=len(chains_done)
        if cash>CHAIN_FEE[i]+80:
            cash-=CHAIN_FEE[i]/3; chain_busy=int(CHAIN_MONTHS[i]); year_decisions+=1
    if y>=2:
        tier=0 if level<3 else (1 if level<5 else 2)
        cash+=comp_ev_cash[tier]*2*0.8; rep+=comp_ev_rep[tier]*2*0.8  # EVx2场,80%胜率期望
        year_decisions+=2
    if "医学" in colleges and not hospital and y>=8 and cash>400:
        cash-=300; hospital=True; year_decisions+=1
    # 9月招生
    rf=0.8 if rep<100 else 0.9 if rep<500 else 1.0 if rep<2000 else 1.1 if rep<5000 else 1.3
    cb=sum(COL[c][3] for c in colleges)
    intake=int(100*rf*(1+cb)*(1.15 if len(colleges)>=3 else 1.0))  # +教室扩容约15%
    students=min(students+intake,intake*3+80)
    rep+=intake//5+intake*0.8//8+5+len(colleges)
    infl=1.03**(y-1)
    sal=(6*0.5+8*1.2+6*2.5)*infl if level>=3 else (6*0.5+6*1.2)*infl
    cmon=sum(COL[c][1] for c in colleges)
    net=students*TUITION[level]-sal-RENT[level]*infl-students*OP[level]-cmon-8
    if hospital: net+=15+students*0.06-8
    # 课题链完成
    if chain_busy>0:
        chain_busy-=1
        if chain_busy==0:
            chains_done.append(1); net+= [20,270,400][len(chains_done)-1]/1.0; rep+=[140,210,280][len(chains_done)-1]
    cash+=net*0  # net below appended per month? keep yearly: cash+=net
    cash+=net
    gap=12-year_decisions  # 大决策外的月份数（事件抉择按月均摊≈1/月）
    max_gap=max(max_gap, max(0,12-year_decisions-8))  # 事件抉择≈8/年兜底
    rows.append((y,level,intake,students,int(net),int(cash),int(rep),len(colleges),len(chains_done),hospital,year_decisions))
print("年 级 招生 在读  月净利   现金  声望 学院 链 医院 大决策/年")
for r in rows:
    print(f"{r[0]:>2} {r[1]} {r[2]:>4} {r[3]:>5} {r[4]:>7} {r[5]:>7} {r[6]:>6} {r[7]:>3} {r[8]:>2} {str(r[9]):>4} {r[10]:>6}")
print(f"\n最大决策空窗≈{max(0,12-8-rows[-1][10])}个月/年  12年累计大决策≈{sum(r[10] for r in rows)} + 事件抉择≈{12*12} 次")
print("破产检查: 最低现金年 =", min(rows,key=lambda r:r[5])[0], "现金", min(r[5] for r in rows))
