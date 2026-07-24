// FilterBar.swift
// M5 F4 首页筛选栏（规格 §4.2 / §4.3）：位于 M4 D4 横幅之下、列表之上；单维 + 组合（AND）筛选，清除=全部。
// 与 M4 D3/D4/S5 三区块共存不冲突：筛选对各区块统一生效（由 TodayTaskViewModel.filtered* 派生）。

import SwiftUI

struct FilterBar: View {
    @Binding var filter: TaskFilter
    let categories: [CategoryDTO]
    let allTags: [TagDTO]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                categoryMenu
                priorityMenu
                tagMenu
                if !filter.isEmpty {
                    Button(action: { filter = TaskFilter() }) {
                        Label("清除筛选", systemImage: "xmark.circle.fill")
                            .font(.subheadline)
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(.horizontal, 4)
        }
        .padding(.vertical, 4)
    }

    // MARK: - 分类筛选（单维；含「全部分类」清除）

    private var categoryMenu: some View {
        Menu {
            Button("全部分类") { filter.categoryId = nil }
            ForEach(categories) { c in
                Button(action: { filter.categoryId = c.id }) {
                    Label(c.name, systemImage: filter.categoryId == c.id ? "checkmark" : "")
                }
            }
        } label: {
            filterLabel(icon: "folder", title: currentCategoryName)
        }
    }

    private var currentCategoryName: String {
        if let id = filter.categoryId, let c = categories.first(where: { $0.id == id }) {
            return c.name
        }
        return "分类"
    }

    // MARK: - 优先级筛选（单维；含「全部优先级」清除）

    private var priorityMenu: some View {
        Menu {
            Button("全部优先级") { filter.priority = nil }
            ForEach(Priority.allCases) { p in
                Button(action: { filter.priority = p }) {
                    Label(p.displayName, systemImage: filter.priority == p ? "checkmark" : "")
                }
            }
        } label: {
            filterLabel(icon: "flag", title: currentPriorityName)
        }
    }

    private var currentPriorityName: String {
        filter.priority?.displayName ?? "优先级"
    }

    // MARK: - 标签筛选（多选取并集 AND + 仅无标签）

    private var tagMenu: some View {
        Menu {
            Button(action: {
                filter.untaggedOnly = true
                filter.tagIds = []   // untaggedOnly 优先于 tagIds（§3.1）
            }) {
                Label("仅无标签", systemImage: filter.untaggedOnly ? "checkmark" : "")
            }
            ForEach(allTags) { t in
                Button(action: {
                    if filter.untaggedOnly { filter.untaggedOnly = false }
                    if filter.tagIds.contains(t.id) { filter.tagIds.remove(t.id) }
                    else { filter.tagIds.insert(t.id) }
                }) {
                    Label(t.name, systemImage: filter.tagIds.contains(t.id) ? "checkmark" : "")
                }
            }
        } label: {
            filterLabel(icon: "tag", title: currentTagName)
        }
    }

    private var currentTagName: String {
        if filter.untaggedOnly { return "无标签" }
        if filter.tagIds.isEmpty { return "标签" }
        return "标签(\(filter.tagIds.count))"
    }

    // MARK: - 通用外观

    private func filterLabel(icon: String, title: String) -> some View {
        Label(title, systemImage: icon)
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.secondary.opacity(0.12)))
    }
}
