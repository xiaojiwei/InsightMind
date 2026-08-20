from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(__file__).parent
FONT = "/Library/Fonts/Managed/LICIUMFONT2022-NORMAL_3947153867.otf"
BOLD = "/Library/Fonts/Managed/LICIUMFONT2022-BOLD_3407592481.otf"

def f(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)

def text(draw, xy, s, size=24, fill="#243b53", bold=False, anchor=None):
    draw.text(xy, s, font=f(size, bold), fill=fill, anchor=anchor, spacing=8)

def rounded(draw, box, fill, radius=24, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def three_layers():
    im = Image.new("RGB", (1600, 900), "#f4f7fb"); d = ImageDraw.Draw(im)
    text(d,(80,55),"灵犀Agent 三层架构",42,"#102a43",True); text(d,(82,105),"动态知识图谱 → 数据基础能力 → 构建发布业务应用",22,"#627d98")
    boxes=[((90,180,1510,360),"#dbeafe","#1e40af","第一层｜动态知识图谱",["技术图谱：数据库、Schema、表、列、主外键、关系、数据特征","业务图谱：指标、维度、口径、层级、别名、维值、事实表关系"]),((90,400,1510,580),"#dcfce7","#166534","第二层｜数据基础能力",["语义目录、名称/别名/维值召回、规则与向量召回、自然语言语义映射","SQL 生成执行、分析洞察、质量校验、版本管理、反馈闭环；Codex 加速构建分析"]),((90,620,1510,800),"#ffedd5","#9a3412","第三层｜构建并发布业务应用",["NLQ 自然语言问数、Insight 智能洞察、Ad-Hoc 查询、看板、Agent / MCP","面向不同业务场景组合指标、维度、权限、分析流程和交互方式"])]
    for box,bg,color,title,lines in boxes:
        # Keep a consistent 22px bottom safety margin so text never crosses the card edge.
        rounded(d,box,bg)
        x, top = box[0] + 50, box[1]
        text(d,(x,top+32),title,30,color,True)
        text(d,(x,top+91),lines[0],22,color)
        text(d,(x,top+129),lines[1],22,color)
    for y in (365,585): d.line((800,y,800,y+28),fill="#486581",width=7); d.polygon([(785,y+25),(815,y+25),(800,y+42)],fill="#486581")
    im.save(OUT/"insightmind_three_layers.png")

def dynamic_kg():
    im=Image.new("RGB",(1600,900),"#f5f8fc"); d=ImageDraw.Draw(im); text(d,(80,55),"灵犀Agent 动态知识图谱",42,"#102a43",True); text(d,(82,105),"技术图谱描述“有什么”，业务图谱描述“是什么意思”",22,"#627d98")
    rounded(d,(90,190,710,710),"#e0f2fe"); rounded(d,(890,190,1510,710),"#ede9fe")
    text(d,(140,245),"技术图谱（Source KG）",32,"#075985",True); lines=["数据库 / Schema / 表 / 列","字段类型、注释、主键、外键","表间关系与可用 Join 路径","基数、受控样本、敏感字段","回答：数据库里有什么？"]
    for i,s in enumerate(lines): text(d,(140,310+i*45),s,24,"#0c4a6e")
    text(d,(940,245),"业务图谱（Business KG）",32,"#5b21b6",True); lines=["指标：名称、定义、口径、单位、公式","维度：层级、时间属性、物理字段","指标/维度与事实表的应用关系","别名、维值、置信度、治理状态","回答：业务指标是什么意思？"]
    for i,s in enumerate(lines): text(d,(940,310+i*45),s,24,"#4c1d95")
    d.line((710,430,890,430),fill="#64748b",width=8); d.polygon([(860,410),(890,430),(860,450)],fill="#64748b"); text(d,(675,385),"映射 / 绑定 / 校验",20,"#475569")
    rounded(d,(270,760,1330,830),"#102a43",35); text(d,(800,795),"Schema 变化 + 图谱重建 + 字典审核 + 反馈 → 动态刷新与可追溯",25,"white",True,"mm")
    im.save(OUT/"insightmind_dynamic_kg.png")

def comparison():
    im=Image.new("RGB",(1600,940),"#f5f7fb"); d=ImageDraw.Draw(im); text(d,(80,55),"方案定位对比",42,"#102a43",True); text(d,(82,105),"三类系统的核心出发点不同",22,"#627d98")
    cols=[(80,530,"#ffffff","SuperSonic","基于语义模型的 ChatBI",["重点：数据集、指标、维度","重点：查询编排与 BI 交互","前提：已有语义模型配置","更偏“消费和编排”"],"#334155"),(575,1025,"#ffffff","大应指标平台 + DataEase","标准指标管理 + BI 消费",["大应：指标定义、口径和服务","DataEase：报表、看板和可视化","重点：标准化管理与消费","更偏“中心化指标平台”"],"#334155"),(1070,1520,"#eff6ff","灵犀Agent","知识图谱驱动的动态语义分析",["自建技术图谱 + 业务图谱","自建语义召回、映射与执行能力","NLQ、洞察、Agent/MCP 闭环","不依赖大应指标平台","更偏“动态构建与持续演化”"],"#1e40af")]
    for x1,x2,bg,title,sub,lines,color in cols:
        rounded(d,(x1,180,x2,790),bg,24,"#2563eb" if title=="灵犀Agent" else None,4 if title=="灵犀Agent" else 1); text(d,((x1+x2)//2,245),title,31,color,True,"mm"); text(d,((x1+x2)//2,300),sub,24,"#475569",""==title,"mm")
        for i,s in enumerate(lines): text(d,(x1+45,380+i*48),"• "+s,23,color)
    rounded(d,(180,850,1420,908),"#1e3a8a",29); text(d,(800,878),"灵犀Agent 的核心差异：知识图谱是统一语义中心，而不是外部指标平台的附属消费层",23,"white",True,"mm")
    im.save(OUT/"architecture_comparison.png")

def cover():
    im=Image.new("RGB",(1600,900),"#0b1f3a"); d=ImageDraw.Draw(im)
    d.ellipse((1080,-160,1720,480),fill="#153e75"); d.ellipse((1220,420,1660,860),fill="#1d4ed8")
    text(d,(100,155),"灵犀Agent",35,"#8fb7ff",True)
    text(d,(100,240),"知识图谱驱动的",64,"white",True)
    text(d,(100,325),"动态语义分析平台",64,"white",True)
    text(d,(105,440),"从数据库结构出发，构建业务语义，形成可执行、可解释、可持续演化的数据能力。",27,"#cbd5e1")
    rounded(d,(100,570,770,660),"#ffffff",20); text(d,(435,615),"动态知识图谱 → 数据基础能力 → 业务应用",27,"#0b1f3a",True,"mm")
    text(d,(105,765),"不依赖外部指标平台｜指标、维度、口径和关系统一来源于知识图谱",23,"#8fb7ff")
    im.save(OUT/"insightmind_cover.png")

def capability_flow():
    im=Image.new("RGB",(1600,900),"#f4f7fb"); d=ImageDraw.Draw(im)
    text(d,(80,55),"数据基础能力链路",42,"#102a43",True); text(d,(82,105),"把知识图谱转化为可执行、可验证的分析能力",22,"#627d98")
    stages=[("用户问题","自然语言 / 上下文","#dbeafe","#1e40af"),("语义召回","名称・别名・维值\n规则・模糊・向量","#e0f2fe","#075985"),("语义映射","指标・维度・时间\n维值・事实表","#dcfce7","#166534"),("质量守门","置信度・歧义\nPII・表范围","#fef3c7","#92400e"),("查询与洞察","SQL 执行・归因\n趋势・异常・解释","#ffedd5","#9a3412")]
    for i,(title,sub,bg,color) in enumerate(stages):
        x=70+i*305; rounded(d,(x,250,x+250,570),bg,24); text(d,(x+125,315),title,30,color,True,"mm")
        for j,line in enumerate(sub.split('\n')): text(d,(x+125,390+j*48),line,23,color,False,"mm")
        if i<4: d.line((x+250,410,x+300,410),fill="#64748b",width=7); d.polygon([(x+285,395),(x+305,410),(x+285,425)],fill="#64748b")
    rounded(d,(210,690,1390,785),"#102a43",28); text(d,(800,737),"Codex 加速：构建知识｜验证质量｜分析问题｜扩展能力",27,"white",True,"mm")
    im.save(OUT/"insightmind_capability_flow.png")

def application_matrix():
    im=Image.new("RGB",(1600,900),"#f6f8fc"); d=ImageDraw.Draw(im)
    text(d,(80,55),"构建并发布业务应用",42,"#102a43",True); text(d,(82,105),"同一套图谱与数据能力，支撑多种业务场景",22,"#627d98")
    cards=[("NLQ 问数","自然语言直达可信数据","#dbeafe","#1e40af"),("Insight 洞察","趋势、归因、异常与解释","#dcfce7","#166534"),("Ad-Hoc","灵活组合指标与维度","#ffedd5","#9a3412"),("经营看板","固化业务视角与监控","#ede9fe","#5b21b6"),("Agent / MCP","将数据能力嵌入智能流程","#cffafe","#0e7490"),("反馈治理","纠正、审核与持续学习","#fce7f3","#9d174d")]
    for i,(title,sub,bg,color) in enumerate(cards):
        row,col=divmod(i,3); x=90+col*500; y=200+row*270; rounded(d,(x,y,x+420,y+210),bg,24); text(d,(x+40,y+60),title,30,color,True); text(d,(x+40,y+125),sub,22,color)
    rounded(d,(300,760,1300,830),"#1e3a8a",28); text(d,(800,795),"一次建模，多场景复用；业务反馈持续反哺知识图谱",25,"white",True,"mm")
    im.save(OUT/"insightmind_application_matrix.png")

def closing():
    im=Image.new("RGB",(1600,900),"#102a43"); d=ImageDraw.Draw(im)
    text(d,(800,120),"汇报结论",42,"#93c5fd",True,"mm")
    text(d,(800,250),"我们的核心不是一个问数界面",48,"white",True,"mm")
    text(d,(800,330),"而是一套可持续演化的知识与数据能力",48,"white",True,"mm")
    points=[("统一语义中心","指标、维度、口径与关系来自知识图谱"),("可信执行闭环","召回、映射、校验、查询与解释贯通"),("快速业务交付","Codex 加速构建，能力快速发布为应用")]
    for i,(a,b) in enumerate(points):
        y=470+i*105; rounded(d,(270,y,1330,y+76),"#183b66",18); text(d,(320,y+38),a,25,"#93c5fd",True,"lm"); text(d,(600,y+38),b,23,"white",False,"lm")
    im.save(OUT/"insightmind_closing.png")

if __name__ == "__main__":
    cover(); three_layers(); dynamic_kg(); capability_flow(); application_matrix(); comparison(); closing()
