#!/usr/bin/env bash
#
# Smart Garden 배포 스크립트
# 맥북에서 코드 수정 후 맥미니로 한 번에 배포.
#
# 사용법:
#   ./deploy.sh                # 프론트 + 백엔드 전체 빌드 & 배포
#   ./deploy.sh backend        # 백엔드만 (프론트 빌드 스킵, 기존 static 유지)
#   ./deploy.sh frontend       # 프론트만 빌드 (jar 재빌드 + 배포)
#   ./deploy.sh restart        # 빌드 없이 맥미니 서비스 재시작만
#   ./deploy.sh logs           # 맥미니 Spring Boot 로그 tail
#
# 요구사항: JDK 21, Node, scp, ssh

set -euo pipefail

# ────────────────────────── 설정 ──────────────────────────
REMOTE_HOST="${REMOTE_HOST:-100.118.127.17}"
REMOTE_USER="${REMOTE_USER:-cultilabs}"
REMOTE_DIR="${REMOTE_DIR:-/Users/cultilabs/smart-garden}"
REMOTE="${REMOTE_USER}@${REMOTE_HOST}"

LAUNCH_LABEL="local.garden.springboot"
JAR_NAME="smart-garden-0.0.1-SNAPSHOT.jar"
REMOTE_JAR_NAME="smart-garden.jar"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/frontend"
BACKEND_DIR="${SCRIPT_DIR}/backend"

# macOS Homebrew Java 21 경로 우선 탐색
if [[ -z "${JAVA_HOME:-}" ]] && [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# ────────────────────────── 색상/로그 ──────────────────────────
BOLD=$'\033[1m'; GREEN=$'\033[32m'; CYAN=$'\033[36m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf "\n${BOLD}${CYAN}▶ %s${RESET}\n" "$*"; }
ok()   { printf "${GREEN}✓${RESET} %s\n" "$*"; }
warn() { printf "${YELLOW}⚠${RESET} %s\n" "$*"; }
err()  { printf "${RED}✗${RESET} %s\n" "$*" >&2; }

# ────────────────────────── 작업 함수 ──────────────────────────
build_frontend() {
    step "프론트엔드 빌드 (Vite → backend/src/main/resources/static)"
    cd "${FRONTEND_DIR}"
    if [[ ! -d node_modules ]]; then
        warn "node_modules 없음. npm install 실행"
        npm install
    fi
    npm run build
    ok "프론트 빌드 완료"
}

build_backend() {
    step "백엔드 빌드 (Gradle bootJar)"
    cd "${BACKEND_DIR}"
    ./gradlew bootJar
    local jar_path="${BACKEND_DIR}/build/libs/${JAR_NAME}"
    if [[ ! -f "${jar_path}" ]]; then
        err "jar 파일 생성 실패: ${jar_path}"
        exit 1
    fi
    ok "jar 생성: $(du -h "${jar_path}" | cut -f1)"
}

upload_jar() {
    step "jar 업로드 → ${REMOTE}:${REMOTE_DIR}/${REMOTE_JAR_NAME}"
    ssh "${REMOTE}" "mkdir -p ${REMOTE_DIR}"
    scp "${BACKEND_DIR}/build/libs/${JAR_NAME}" \
        "${REMOTE}:${REMOTE_DIR}/${REMOTE_JAR_NAME}"
    ok "업로드 완료"
}

restart_service() {
    step "맥미니 Spring Boot 재시작 (launchd)"
    ssh "${REMOTE}" "launchctl kickstart -k gui/\$(id -u)/${LAUNCH_LABEL}" || {
        warn "launchctl kickstart 실패 → unload/load 재시도"
        ssh "${REMOTE}" "launchctl unload ~/Library/LaunchAgents/${LAUNCH_LABEL}.plist 2>/dev/null; \
                         launchctl load ~/Library/LaunchAgents/${LAUNCH_LABEL}.plist"
    }
    ok "재시작 요청 전송됨"
}

health_check() {
    step "Health check (http://${REMOTE_HOST}:8080)"
    local i
    for i in 1 2 3 4 5 6 7 8 9 10; do
        if curl -sf --max-time 3 -o /dev/null "http://${REMOTE_HOST}:8080/api/device/status"; then
            ok "백엔드 응답 OK"
            curl -s "http://${REMOTE_HOST}:8080/api/sensors/current" | python3 -m json.tool || true
            return 0
        fi
        printf "."; sleep 1
    done
    echo
    err "Health check 실패. ssh ${REMOTE} 'tail -50 ${REMOTE_DIR}/spring.log' 로 로그 확인"
    return 1
}

tail_logs() {
    step "맥미니 로그 tail (Ctrl+C 로 종료)"
    ssh "${REMOTE}" "tail -f ${REMOTE_DIR}/spring.log"
}

usage() {
    cat <<EOF
Usage: $0 [all|backend|frontend|restart|logs]

  (no args)   프론트 + 백엔드 빌드 & 배포 & 재시작 & health check
  all         위와 동일
  backend     백엔드만 빌드 & 배포
  frontend    프론트 빌드 → jar 재빌드 & 배포 (프론트 변경 시)
  restart     빌드 없이 재시작만
  logs        맥미니 Spring Boot 로그 tail
EOF
}

# ────────────────────────── 라우팅 ──────────────────────────
cmd="${1:-all}"
case "${cmd}" in
    all|"")
        build_frontend
        build_backend
        upload_jar
        restart_service
        health_check
        ;;
    backend)
        build_backend
        upload_jar
        restart_service
        health_check
        ;;
    frontend)
        build_frontend
        build_backend
        upload_jar
        restart_service
        health_check
        ;;
    restart)
        restart_service
        health_check
        ;;
    logs)
        tail_logs
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        err "알 수 없는 명령: ${cmd}"
        usage
        exit 1
        ;;
esac

echo
ok "완료 · http://${REMOTE_HOST}:8080"
