package computera.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GuideBookScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 230;
    private static final int LINE = 10;
    private static final int MAX_LINES = 16;

    public record Page(String title, String body) {
    }

    private static final List<Page> PAGES = List.of(
            new Page("1. 计算机的组成",
                    "机柜是计算机本体，内部有 8 个计算卡槽。\n"
                    + "计算卡决定线程数：I=2、II=4、III=8、IV=16、V=32。\n"
                    + "终端是操作界面：电传终端用纸带，CRT 终端用电。\n"
                    + "用数据线缆把机柜、终端和设备连成同一个集群。\n"
                    + "集群规则：面贴或线缆、同维度、线缆 ≤64 格、最多 32 台机柜。"),
            new Page("2. 搭建第一台电脑",
                    "1) 放机柜，手持计算卡右键插入（或右键打开界面拖入）。\n"
                    + "2) 机柜旁放线缆，再放 CRT 终端。\n"
                    + "3) 给机柜供电：EU 适配器贴机柜并接 IC2 电网，或用其他模组的 FE 线缆。\n"
                    + "4) 电传终端需要先拿纸带右键装纸（8192 行/卷）。\n"
                    + "5) 右键 CRT 打开调度界面。"),
            new Page("3. CRT 界面",
                    "顶部显示线程占用和运行状态。\n"
                    + "终端支持多人同时打开：共用一个线程和同一份输出，各关各的。\n"
                    + "程序列表每行有：运行、编辑、删除。\n"
                    + "底部：文件名 + 创建、停止、刷新、物流、文本。\n"
                    + "「文本」切到纯命令行终端，可以敲下面这些命令。"),
            new Page("4. 终端命令",
                    "help 命令列表    ls 文件列表    cat 查看文件\n"
                    + "edit 打开编辑器    run 运行    stop 停止    rm 删除\n"
                    + "devices 列出可控制设备（名字+坐标）\n"
                    + "ports 列出物流端口    count/find 查询物品\n"
                    + "store/take 背包与端口之间存取\n"
                    + "export/import/drive 硬盘与闪存"),
            new Page("5. 脚本入门",
                    "语言是 Python 子集：缩进、if/while/for、def、列表/字典。\n"
                    + "内置：print、len、range、int、float、str、bool、sleep(毫秒)、time()、log。\n"
                    + "注意：字典取值用 cell[\"id\"]，不能写 cell.id；\n"
                    + "不支持 import、try/except、类。\n"
                    + "编辑器：Enter 换行，↑↓ 切换行，Backspace 空行删除，Esc 保存返回。"),
            new Page("6. 设备 API",
                    "devices.<名字> 访问容器：\n"
                    + "size() 槽位数    get(slot) 返回 {id,count}\n"
                    + "id(slot) 物品 ID    count(slot) 数量\n"
                    + "move(from,to,n) 同容器移动\n"
                    + "logistics.count(id) 全网数量    logistics.find(id) 分布\n"
                    + "设备名字用 devices 命令查询。"),
            new Page("7. 物流端口与管道",
                    "物流端口贴在容器上。手持物品右键设置过滤，空手查看，潜行+空手清除。\n"
                    + "管道会把源容器里匹配过滤的物品送进目标端口容器。\n"
                    + "吞吐：每条管道每 tick 4 个。\n"
                    + "不设过滤的端口是源；设了过滤的端口是目标。"),
            new Page("8. 物流页操作",
                    "CRT → 物流。\n"
                    + "左键拿起整组/放下，右键拿半组/放一个。\n"
                    + "分配模式：勾选多个容器标签（金色），点源格平均分配。\n"
                    + "发放模式：点容器格子取参照物（或手持物品），勾目标，设每箱数量后点发放。\n"
                    + "标签 > 6 个用 < > 滚动；容器改名、翻页、< 1/2 >。\n"
                    + "「原界面」打开方块自己的 GUI，受限槽位用它。"),
            new Page("9. 电力与无线",
                    "CRT 终端从集群机柜扣 3 FE/t。机柜不发电。\n"
                    + "EU 适配器把 IC2 的 EU 转成 FE（1 EU = 4 FE）供给相邻机柜或数据线缆连接的集群。\n"
                    + "网络连接器：右键机柜设 hub，再右键容器无线绑定。\n"
                    + "无线容器可在物流页按名字使用。\n"
                    + "打孔机：空白卡写入相邻终端当前文件，程序卡写回机柜。"),
            new Page("10. 网络显示屏",
                    "显示屏贴在完整方块的侧面，像展示框一样。\n"
                    + "多块同朝向、彼此相邻的屏会自动拼成一块大屏，只显示一份内容。\n模式在 CRT 界面「屏显」页切换：总览/物品/容器。\n容器模式每行显示：名字 数量 状态（运行/有货/空闲）。\n"
                    + "屏幕每 3 秒刷新集群状态：物品/种类、容器/活跃、机柜/终端、线程、电力。\n"
                    + "屏幕要贴着机柜或线缆才算接入集群。\n右键屏幕 = 打开集群终端（和 CRT 一样的界面）；潜行+右键 = 屏幕状态页。"),
            new Page("11. 集装箱",
                    "单个集装箱 27 格，等于一个原版箱子。\n"
                    + "面贴的集装箱会合并成一个容器，格位相加，最多 64 个（1728 格）。\n"
                    + "拆掉其中一个只会掉它自己的物品，剩下的重新分组；\n"
                    + "断成两堆就变成两个独立容器，各自保留自己的物品。\n"
                    + "超过 64 个时第 65 个会放不下去。翻页按钮每页 54 格；\n"
                    + "CRT 物流页把它当一个容器，槽位自动翻页。"));

    private int page;
    private int left;
    private int top;
    private final List<String> wrapped = new ArrayList<>();

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideBookScreen());
    }

    public GuideBookScreen() {
        super(Component.translatable("item.computera.guide_book"));
    }

    @Override
    protected void init() {
        left = (this.width - WIDTH) / 2;
        top = (this.height - HEIGHT) / 2;
        rebuild();
    }

    private void rebuild() {
        wrapped.clear();
        Page p = PAGES.get(page);
        for (String para : p.body().split("\n")) {
            String rest = para;
            while (this.font.width(rest) > WIDTH - 30) {
                int cut = rest.length();
                while (cut > 1 && this.font.width(rest.substring(0, cut)) > WIDTH - 30) {
                    cut--;
                }
                wrapped.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            wrapped.add(rest);
        }
        if (wrapped.size() > MAX_LINES) {
            wrapped.subList(MAX_LINES, wrapped.size()).clear();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int y = top + HEIGHT - 24;
        if (inside(event.x(), event.y(), left + WIDTH - 110, y, 46, 16)) {
            if (page > 0) {
                page--;
                rebuild();
            }
            return true;
        }
        if (inside(event.x(), event.y(), left + WIDTH - 58, y, 46, 16)) {
            if (page + 1 < PAGES.size()) {
                page++;
                rebuild();
            }
            return true;
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFFF0E6C8);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF8A6A3A);
        graphics.fill(left, top + HEIGHT - 2, left + WIDTH, top + HEIGHT, 0xFF8A6A3A);

        Page p = PAGES.get(page);
        graphics.text(this.font, p.title(), left + 12, top + 10, 0xFF3A2A1A, false);
        int y = top + 26;
        for (String line : wrapped) {
            graphics.text(this.font, line, left + 14, y, 0xFF2A2218, false);
            y += LINE;
        }

        graphics.text(this.font, Component.literal((page + 1) + "/" + PAGES.size()),
                left + WIDTH - 150, top + HEIGHT - 20, 0xFF6A5A3A, false);
        drawButton(graphics, left + WIDTH - 110, top + HEIGHT - 24, 46, 16,
                Component.translatable("computera.gui.guide.prev"), mouseX, mouseY);
        drawButton(graphics, left + WIDTH - 58, top + HEIGHT - 24, 46, 16,
                Component.translatable("computera.gui.guide.next"), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFFD8C8A0 : 0xFFCDBB92);
        graphics.fill(x, y, x + w, y + 1, 0xFF8A6A3A);
        int tx = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, tx, y + (h - 8) / 2, 0xFF3A2A1A, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
