# 脚本目录说明（主链路与历史脚本）

## 主链路脚本（建议保留）
1. `full_auto_launch.ps1`
2. `one_click_no_cli.ps1`
3. `init_db_python.py`
4. `smoke_test.py`
5. `export_analysis_report.py`
6. `start_backend.ps1`
7. `start_frontend.ps1`

对应入口：
1. `OneClick_Full_Auto_Launch.bat`
2. `OneClick_NoCLI_Init_Test.bat`

## 历史/实验脚本（按需使用）
以下脚本不在“一键部署与主链路”中，不建议作为日常运行入口：
1. `generate_thesis_word_docs.py`
2. `rebuild_db_from_bilibili_csv.py`
3. `import_douyin_dataset_csv.py`
4. `clean_html_data.py`
5. `start_all.ps1`

建议：
1. 仅在明确需要时再执行历史脚本。
2. 新功能优先接入主链路脚本，避免出现多入口行为不一致。
