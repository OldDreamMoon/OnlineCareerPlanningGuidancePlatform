.PHONY: preflight prepare-env up down app-up app-down smoke agent-start

preflight:
	bash scripts/dev/00_preflight_check.sh

prepare-env:
	bash scripts/dev/01_prepare_env_files.sh

up:
	bash scripts/dev/04_start_services.sh

down:
	bash scripts/dev/08_stop_services.sh

app-up:
	bash scripts/dev/05_start_apps_local.sh

app-down:
	bash scripts/dev/06_stop_apps_local.sh

smoke:
	bash scripts/dev/07_smoke_check.sh

agent-start:
	bash scripts/agent/00_start_agent_session.sh
