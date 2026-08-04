# -*- coding: utf-8 -*-
import io, os, sys

ANCHOR = 'setting_provider_page_vertex_ai'

# key -> list of (locale, translation)
STRINGS = {
    'setting_catalog_page_title': {
        'zh': '模型目录', 'zh-rTW': '模型目錄', 'ja': 'カタログ',
        'ko-rKR': '카탈로그', 'ru': 'Каталог', 'ar': 'الكتالوج',
    },
    'setting_catalog_page_search_placeholder': {
        'zh': '搜索提供商…', 'zh-rTW': '搜尋提供商…', 'ja': 'プロバイダを検索…',
        'ko-rKR': '프로바이더 검색…', 'ru': 'Поиск провайдеров…', 'ar': 'ابحث عن المزودين…',
    },
    'setting_catalog_page_add_provider': {
        'zh': '添加提供商', 'zh-rTW': '新增提供商', 'ja': 'プロバイダを追加',
        'ko-rKR': '프로바이더 추가', 'ru': 'Добавить провайдера', 'ar': 'إضافة مزود',
    },
    'setting_catalog_page_api_key_label': {
        'zh': 'API 密钥', 'zh-rTW': 'API 金鑰', 'ja': 'API キー',
        'ko-rKR': 'API 키', 'ru': 'API-ключ', 'ar': 'مفتاح API',
    },
    'setting_catalog_page_api_key_placeholder': {
        'zh': '粘贴你的 API 密钥', 'zh-rTW': '貼上你的 API 金鑰', 'ja': 'API キーを貼り付け',
        'ko-rKR': 'API 키를 붙여넣기', 'ru': 'Вставьте API-ключ', 'ar': 'الصق مفتاح API',
    },
    'setting_catalog_page_base_url_label': {
        'zh': '基础 URL', 'zh-rTW': '基礎 URL', 'ja': 'ベース URL',
        'ko-rKR': '기본 URL', 'ru': 'Базовый URL', 'ar': 'الرابط الأساسي',
    },
    'setting_catalog_page_api_format_label': {
        'zh': 'API 格式', 'zh-rTW': 'API 格式', 'ja': 'API 形式',
        'ko-rKR': 'API 형식', 'ru': 'Формат API', 'ar': 'صيغة API',
    },
    'setting_catalog_page_default_models_label': {
        'zh': '默认模型', 'zh-rTW': '預設模型', 'ja': 'デフォルトモデル',
        'ko-rKR': '기본 모델', 'ru': 'Модели по умолчанию', 'ar': 'النماذج الافتراضية',
    },
    'setting_catalog_page_signup_link': {
        'zh': '注册', 'zh-rTW': '註冊', 'ja': 'サインアップ',
        'ko-rKR': '가입', 'ru': 'Регистрация', 'ar': 'سجّل',
    },
    'setting_catalog_page_api_key_link': {
        'zh': '获取 API 密钥', 'zh-rTW': '取得 API 金鑰', 'ja': 'API キーを取得',
        'ko-rKR': 'API 키 받기', 'ru': 'Получить API-ключ', 'ar': 'احصل على مفتاح API',
    },
    'setting_catalog_page_add_success': {
        'zh': '提供商已添加 — 请前往设置 → 提供商中启用', 'zh-rTW': '提供商已新增 — 請前往設定 → 提供商中啟用',
        'ja': 'プロバイダを追加しました — 設定 → プロバイダから有効化してください',
        'ko-rKR': '프로바이더를 추가했습니다 — 설정 → 프로바이더에서 활성화하세요',
        'ru': 'Провайдер добавлен — включите его в Настройки → Провайдеры',
        'ar': 'تمت إضافة المزود — قم بتفعيله من الإعدادات ← المزودون',
    },
    'setting_catalog_page_add_failed': {
        'zh': '无法添加提供商', 'zh-rTW': '無法新增提供商', 'ja': 'プロバイダを追加できません',
        'ko-rKR': '프로바이더를 추가할 수 없습니다', 'ru': 'Не удалось добавить провайдера', 'ar': 'تعذر إضافة المزود',
    },
    'setting_catalog_page_disabled_badge': {
        'zh': '已禁用', 'zh-rTW': '已停用', 'ja': '無効',
        'ko-rKR': '비활성화됨', 'ru': 'Отключено', 'ar': 'معطّل',
    },
    'setting_catalog_page_models_count': {
        'zh': '%1$d 个模型', 'zh-rTW': '%1$d 個模型', 'ja': '%1$d モデル',
        'ko-rKR': '모델 %1$d개', 'ru': '%1$d моделей', 'ar': '%1$d نماذج',
    },
    'setting_provider_page_catalog': {
        'zh': '目录', 'zh-rTW': '目錄', 'ja': 'カタログ',
        'ko-rKR': '카탈로그', 'ru': 'Каталог', 'ar': 'الكتالوج',
    },
}

base = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'res'))

for locale, trans in list(STRINGS['setting_catalog_page_title'].items()) + [('en', None)]:
    pass

for locale in ['zh', 'zh-rTW', 'ja', 'ko-rKR', 'ru', 'ar']:
    path = os.path.normpath(os.path.join(base, 'values-' + locale, 'strings.xml'))
    with io.open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    if 'setting_catalog_page_title' in content:
        print(f'{locale}: already present, skipping')
        continue
    anchor = '<string name="%s">' % ANCHOR
    idx = content.find(anchor)
    if idx < 0:
        print(f'{locale}: ANCHOR NOT FOUND')
        continue
    # find end of anchor line
    eol = content.find('\n', idx)
    lines = []
    for key in STRINGS:
        lines.append('  <string name="%s">%s</string>' % (key, STRINGS[key][locale]))
    insert = '\n' + '\n'.join(lines) + '\n'
    new_content = content[:eol+1] + insert + content[eol+1:]
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f'{locale}: inserted {len(lines)} strings')
