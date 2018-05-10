/*
 *
 *  Bluetooth low-complexity, subband codec (SBC) library
 *
 *  Copyright (C) 2004-2006  Marcel Holtmann <marcel@holtmann.org>
 *  Copyright (C) 2004-2005  Henryk Ploetz <henryk@ploetzli.ch>
 *  Copyright (C) 2005-2006  Brad Midgley <bmidgley@xmission.com>
 *
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

#ifndef __SBC_H
#define __SBC_H

#ifdef __cplusplus
extern "C" {
#endif


struct sbc_struct {
    unsigned long flags;
    unsigned short rate;
    unsigned char channels;
    unsigned char joint;
    unsigned char blocks;
    unsigned char subbands;
    unsigned char bitpool;

    void *data;
    int size;
    int len;

    unsigned long duration;

    void *priv;
};

typedef struct sbc_struct sbc_t;

int sbc_init(sbc_t *sbc, unsigned long flags);

int sbc_reinit(sbc_t *sbc, unsigned long flags);


int sbc_encode(sbc_t *sbc,
               void *input,
               int input_len,
               void *output,
               int output_len,
               int *written);

int sbc_decode(sbc_t *sbc,
               void *input,
               int input_len,
               void *output,
               int output_len,
               int *written);

void sbc_finish(sbc_t *sbc);

#ifdef __cplusplus
}
#endif

#endif /* __SBC_H */
