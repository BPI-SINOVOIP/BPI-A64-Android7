#ifndef VIDDEC_PARSER_OPS_H
#define VIDDEC_PARSER_OPS_H

#include "viddec_fw_workload.h"

#define VIDDEC_PARSE_INVALID_POS 0xFFFFFFFF

typedef enum
{
    VIDDEC_PARSE_EOS = 0x0FFF, /* Dummy start code to force EOS */
    VIDDEC_PARSE_DISCONTINUITY,  /* Dummy start code to force completion and flush */
}viddec_parser_inband_messages_t;

typedef struct
{
    uint32_t context_size;
    uint32_t persist_size;
}viddec_parser_memory_sizes_t;

typedef struct
{
    void  (*init)(void *ctxt, uint32_t *persist, uint32_t preserve);
    uint32_t (*parse_sc) (void *ctxt, void *pcxt, void *sc_state);
    uint32_t (*parse_syntax) (void *parent, void *ctxt);
    void (*get_cxt_size) (viddec_parser_memory_sizes_t *size);
    uint32_t (*is_wkld_done)(void *parent, void *ctxt, uint32_t next_sc, uint32_t *codec_specific_errors);
    uint32_t (*is_frame_start)(void *ctxt);
    uint32_t (*gen_contrib_tags)(void *parent, uint32_t ignore_partial);
    uint32_t (*gen_assoc_tags)(void *parent);
}viddec_parser_ops_t;


typedef enum
{
    VIDDEC_PARSE_ERROR = 0xF0,
    VIDDEC_PARSE_SUCESS = 0xF1,
    VIDDEC_PARSE_FRMDONE = 0xF2,
}viddec_parser_error_t;

/*
 *
 *Functions used by Parsers 
 *
 */

/* This function returns the requested number of bits(<=32) and increments au byte position.
 */
int32_t viddec_pm_get_bits(void *parent, uint32_t *data, uint32_t num_bits);

/* This function returns requested number of bits(<=32) with out incrementing au byte position 
 */
int32_t viddec_pm_peek_bits(void *parent, uint32_t *data, uint32_t num_bits);

/* This function skips requested number of bits(<=32) by incrementing au byte position.
 */
int32_t viddec_pm_skip_bits(void *parent, uint32_t num_bits);

/* This function appends a work item to current workload.
 */
int32_t viddec_pm_append_workitem(void *parent, viddec_workload_item_t *item);

/* This function appends a work item to next workload.
 */
int32_t viddec_pm_append_workitem_next(void *parent, viddec_workload_item_t *item);

/* This function gets current byte and bit positions and information on whether an emulation byte is present after
current byte.
 */
int32_t viddec_pm_get_au_pos(void *parent, uint32_t *bit, uint32_t *byte, unsigned char *is_emul);

/* This function appends Pixel tag to current work load starting from current position to end of au unit.
 */
int32_t viddec_pm_append_pixeldata(void *parent);

/* This function appends Pixel tag to next work load starting from current position to end of au unit.
 */
int32_t viddec_pm_append_pixeldata_next(void *parent);

/* This function provides the workload header for pasers to fill in attribute values
 */
viddec_workload_t* viddec_pm_get_header(void *parent);

/* This function provides the next workload header for pasers to fill in attribute values
 */
viddec_workload_t* viddec_pm_get_next_header(void *parent);

/* Returns the current byte value where offset is on */
uint32_t viddec_pm_get_cur_byte(void *parent, uint8_t *byte);

/* Tells us if there is more data that need to parse */
int32_t viddec_pm_is_nomoredata(void *parent);

/* This function appends misc tag to work load starting from start position to end position of au unit */
int32_t viddec_pm_append_misc_tags(void *parent, uint32_t start, uint32_t end, viddec_workload_item_t *wi, uint32_t using_next);

void viddec_pm_set_next_frame_error_on_eos(void *parent, uint32_t error);

void viddec_pm_set_late_frame_detect(void *parent);

static inline void viddec_fw_reset_workload_item(viddec_workload_item_t *wi)
{
    wi->vwi_payload[0] = wi->vwi_payload[1] = wi->vwi_payload[2] = 0;
}

void viddec_pm_setup_userdata(viddec_workload_item_t *wi);
#endif
