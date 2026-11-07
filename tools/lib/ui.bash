# SPDX-License-Identifier: MIT
# The task scripts' messages and questions: gum's, styled, on a terminal; plain lines where there is
# none (CI, hk, the tests), which read as "<task>: <message> key=value". A script sets ui_prefix,
# its task's name, then sources this.
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.

ui_prefix=${ui_prefix:-task}
ui_tty=/dev/tty

ui_styled() {
	[[ -t 2 ]] && command -v gum >/dev/null 2>&1
}

# log <level> <message> [<key> <value>]...
log() {
	local level=$1 message=$2 line
	shift 2
	if ui_styled; then
		gum log --structured --level "$level" --prefix "$ui_prefix" "$message" "$@"
		return
	fi
	line="$ui_prefix: $message"
	while (($# >= 2)); do
		line="$line $1=$2"
		shift 2
	done
	echo "$line" >&2
}

info() {
	log info "$@"
}

warn() {
	log warn "$@"
}

die() {
	log error "$@"
	exit 1
}

# markdown: renders the Markdown on stdin as glow does (gum format, glamour's auto style, light or dark
# to suit the terminal) where stdout is a terminal. Elsewhere (CI, a pipe, the tests) it prints the
# Markdown as it is. GUM_FORMAT_THEME picks another of glamour's styles.
markdown() {
	if [[ -t 1 ]] && command -v gum >/dev/null 2>&1; then
		gum format --theme "${GUM_FORMAT_THEME:-auto}"
		return
	fi
	cat
}

# confirm <question>: true on yes, from the terminal; no terminal to ask on is a no.
# Cancelled (ctrl-c, esc), the script stops.
confirm() {
	local question=$1 answer status=0
	if ui_styled && [[ $ui_tty = /dev/tty ]] && { : </dev/tty; } 2>/dev/null; then
		gum confirm --default=false "$question" </dev/tty || status=$?
		case $status in
		0) return 0 ;;
		1) return 1 ;;
		*) die "cancelled" ;;
		esac
	fi
	{ : <"$ui_tty"; } 2>/dev/null || return 1
	read -r -p "$question [y/N] " answer <"$ui_tty"
	[[ "$answer" = [yY]* ]]
}
