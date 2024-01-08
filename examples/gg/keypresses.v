import gg
struct App{
	pub  mut:
	current gg.Event
	buffer map[string]string
}
mut a:= &App{}

gg.start(
	window_title: 'Hello'
	bg_color: gg.Color{240, 240, 128, 255}
	width: 320
	height: 240
	event_fn: fn [mut a](e &gg.Event, data voidptr) {
		a.current=e
		cb := e.key_code.str()
		cc := e.char_code
		cd := if cb == 'invalid' { [u8(cc & 0xFF)].bytestr() } else { cb }
		if e.typ == .char {
			a.buffer[[u8(cc & 0xFF)].bytestr()]=[u8(cc & 0xFF)].bytestr()
		} else if e.typ == .key_up {
				a.buffer[cb]="-"
		}
	}
	frame_fn: fn [a](mut ctx &gg.Context) {
		ctx.begin()
		ctx.draw_text(40, 100, 'GG frame: ${ctx.frame:06} ${a.buffer}',
			size: 30
			color: gg.Color{50, 50, 255, 255}
		)
		ctx.show_fps()
		ctx.end()
	}
)