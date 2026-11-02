/* modecore: Mode's native layer (CoreDevice remote control).
 *
 * Every function returning char* returns NULL on success, or an error message
 * the caller frees with mc_string_free. Coordinates are 0..65535 across the
 * screen, top-left origin. A session is used from one thread at a time.
 * Keep in sync with native/modecore/src/ffi.rs. */
#ifndef MODECORE_H
#define MODECORE_H

#include <stddef.h>
#include <stdint.h>

typedef struct McSession McSession;

/* disk_image_dir (nullable): unpacked iOS DDI Restore folder, mounted first if the
 * phone has none (after a restart; needs internet for Apple's signature).
 * out_mounted (nullable): set to 1 when it was mounted. */
char *mc_session_open_rppairing(const char *host, uint16_t port, const char *hostname,
                                const uint8_t *pairing, size_t pairing_len,
                                const char *disk_image_dir, McSession **out,
                                uint8_t *out_mounted);
void mc_session_close(McSession *s);
/* Stop the screen stream and keep the session. Resume before any touch. */
char *mc_session_pause_stream(McSession *s);
char *mc_session_resume_stream(McSession *s);

/* clock: 0 = mach_absolute_time, 1 = wall clock */
char *mc_set_clock(McSession *s, uint8_t clock);

char *mc_touch(McSession *s, uint16_t x1, uint16_t y1, uint16_t x2, uint16_t y2,
               uint32_t hold_ms, uint32_t travel_ms);
/* button: 0 = home, 1 = lock, 2 = volume up, 3 = volume down */
char *mc_button(McSession *s, uint8_t button);
char *mc_key_named(McSession *s, const char *name);
char *mc_type_ascii(McSession *s, const char *utf8);
char *mc_paste_text(McSession *s, const char *utf8);
char *mc_set_clipboard(McSession *s, const char *utf8);

/* PNG, full resolution; free with mc_bytes_free */
char *mc_screenshot(McSession *s, uint8_t **out, size_t *out_len);
char *mc_launch(McSession *s, const char *bundle_id);
/* closes a running copy first, so the app starts on its home page */
char *mc_launch_fresh(McSession *s, const char *bundle_id);
char *mc_list_apps(McSession *s, char **out_json);

void mc_string_free(char *s);
void mc_bytes_free(uint8_t *ptr, size_t len);

#endif
