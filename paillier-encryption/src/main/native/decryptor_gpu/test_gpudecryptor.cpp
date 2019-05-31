/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

#include "gpudecryptor.h"

#include <gmp.h>

#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>

#define NUM_CIPHERTEXTS 16384

const int num_ciphertexts = NUM_CIPHERTEXTS;
const int ciphertext_byte_size = 768;
const int plaintext_byte_size = 384;
const char p_str[] = "1734678562027645515810951398707132692499353463992903506650419607176459162278123321474387486086919903410312749353085878702146869051547388821194540318970081237813997623740218298724897147400036992745425505555134626052879546227148965177129940892632996507082302983939861957365206559977851526738263803944784433507800342853134676374601219953579351091835412079017648055280107444011150867909994015226957895926349366513131352083042718385964999130137525931744723625777614227";
const char q_str[] = "2104048478502153233116171604814443362180467397946639774783134058812961348287180240761533717501990285972807725643000535203049527623400384618681963913689990484283606035775287442908707134931439567185916019228671466901551561826583405565161474058033162433766785569690805817289284758236119606473940543358154284673366578411770538343748280028114324498142468441138868572300993952941245810233541436738436596177719443820955439667312098904847112561458179448103313502496897483";
const char p1_str[] = "74131532986875017333770990102104387287544798010726606076957946336460767820361";
const char q1_str[] = "79987225133753711074568869813950567962190446680388243682266829788745821384123";
const char c_str[] = "4914143129357748730243018981154980753236302298902735232789188667148442647464465760027355346579409158479887494746300641575947481436367810979499740305793850978335165846757461981762734725577697459148042937934680754611998223531746300577743723893333019369377164944808729408871164038820715030209077911730548743777154131662636443959881973333709012595406484753713562341892011031983666360355487377380295308998090616164935245408117429538222995014237519388911711805187201222240328180620822341146347162942730438895509986140053671750721417651742793320808917091993022689367602942491199240042908991070945301566189638579590589679876963114884753921015770430075722672299467589225765006200001932150505039144720723181077957711910873133237251933670015555089483543462898896227207766134943493189410803498666856012056950623567933568605924559293912395078642966009050975832409879091381029272405727889004429361375836041922892744742112872986090449092644749725348711609219295462051619238823866034275261172658173420228848853223506717488470255200970187060888279210410240502676074441722160195351982164494450087325503793032599593910117558196577723531153871975714953922166352834402126966753947609958148925867298948278557615430304158427648897345107645458922712775601778527508958346531449942092859187989459185163506603223125415656893447020206834455045728862305303139695835886407766747972045219238559317477581719674943761950791381283778201169941556628221908684618250808006934407999366698181395367957401150583651990549701021906560747604342531207235426601187830078639395174628083829328706704746628095733223404922425669858499953552863915950805602825719039122401341909341744617667250888535458068304170212015070267475173141167121126073749333069443457753031234032916546329161761069205570039326483971162761409788341992207867620284923746862967720124716357796892360613823234491528109051783613226";
const char d_str[] = "19415327908864142275349536639227666449982031337007627492050022960";

uint8_t buf[NUM_CIPHERTEXTS * 768];


int check_privkey(mpz_t p, mpz_t q, mpz_t p1, mpz_t q1) {
    int success = 0;
    mpz_t tmp;
    mpz_init(tmp);
    mpz_sub_ui(tmp, p, 1);
    mpz_fdiv_r(tmp, tmp, p1);
    if (mpz_cmp_ui(tmp, 0)) {
	printf ("Error: p1 does not divide p-1\n");
	goto done;
    }
    mpz_sub_ui(tmp, q, 1);
    mpz_fdiv_r(tmp, tmp, q1);
    if (mpz_cmp_ui(tmp, 0)) {
	printf ("Error: q1 does not divide q-1\n");
	goto done;
    }
    success = 1;
 done:
    mpz_clear(tmp);
    return success;
}

#define TEST_BEGIN(func) \
    printf ("running test %s\n", (func));

#define TEST_END(func,succ)			\
    if (success) {				\
	printf ("%s: passed\n", (func));	\
    } else {					\
	printf ("%s: FAILED\n", (func));	\
    }
    
int _test_batch_decrypt(handle_t h_context, mpz_t N, bool use_gpu) {
    //TEST_BEGIN(__func__);
    int success = 0;

    mpz_t N2;
    mpz_init(N2);
    mpz_mul(N2, N, N);

    mpz_t plaintexts[num_ciphertexts];
    mpz_t ciphertexts[num_ciphertexts];
    int i;
    for (i = 0; i < num_ciphertexts; i++) {
	mpz_init(plaintexts[i]);
	mpz_init(ciphertexts[i]);
    }
    mpz_set_str(plaintexts[0], d_str, 10);
    mpz_set_str(ciphertexts[0], c_str, 10);

    for (i = 1; i < 5; i++) {
	mpz_set(plaintexts[i], plaintexts[0]);
	mpz_set(ciphertexts[i], ciphertexts[0]);
    }

    for (i = 5; i < num_ciphertexts; i++) {
	// plaintext[i] = 2 * plaintext[i-1]  mod  N
	// ciphertext[i] = ciphertext[i-1]^2  mod  N^2
	mpz_mul_ui(plaintexts[i], plaintexts[i-1], 2);
	mpz_fdiv_r(plaintexts[i], plaintexts[i], N);
	mpz_mul(ciphertexts[i], ciphertexts[i-1], ciphertexts[i-1]);
	mpz_fdiv_r(ciphertexts[i], ciphertexts[i], N2);
    }

    memset(buf, 0, sizeof(buf));
    for (i = 0; i < num_ciphertexts; i++) {
	int padding = ciphertext_byte_size - (mpz_sizeinbase(ciphertexts[i], 2) + 7) / 8;
	assert (padding >= 0);
	mpz_export(&buf[i * ciphertext_byte_size + padding], NULL, 1, 1, 1, 0, ciphertexts[i]);
    }

    if (0 != gpudecryptor_decrypt_batch(h_context, buf, num_ciphertexts, use_gpu)) {
	printf ("gpudecryptor_decrypt_batch() failed!\n");
	goto done;
    }

    // check answers
    success = 1;
    mpz_t plaintext2;
    mpz_init(plaintext2);
    for (i = 0; i < num_ciphertexts; i++) {
	mpz_import(plaintext2, plaintext_byte_size, 1, 1, 1, 0, &buf[i * plaintext_byte_size]);
	if (mpz_cmp(plaintext2, plaintexts[i])) {
	    success = 0;
	}
    }

    mpz_clear(plaintext2);

 done:
    for (i = 0; i < num_ciphertexts; i++) {
	mpz_clear(plaintexts[i]);
	mpz_clear(ciphertexts[i]);
    }
    mpz_clear(N2);

    //TEST_END(__func__, success);
    return success;
}

int test_batch_decrypt_gpu(handle_t h_context, mpz_t N) {
    TEST_BEGIN(__func__);
    int success = _test_batch_decrypt(h_context, N, true);
    TEST_END(__func__, success);
    return success;
}

int test_batch_decrypt_cpu(handle_t h_context, mpz_t N) {
    TEST_BEGIN(__func__);
    int success = _test_batch_decrypt(h_context, N, false);
    TEST_END(__func__, success);
    return success;
}

int main() {

    mpz_t p, q, p1, q1, N;
    mpz_init_set_str(p, p_str, 10);
    mpz_init_set_str(q, q_str, 10);
    mpz_init_set_str(p1, p1_str, 10);
    mpz_init_set_str(q1, q1_str, 10);
    mpz_init(N);
    mpz_mul(N, p, q);

    if (!check_privkey(p, q, p1, q1)) {
	printf ("check_privkey() failed!\n");
	return -1;
    }

    assert (initialize_native_library());

    handle_t h_context = gpudecryptor_new(p, q, p1, q1);
    if (HANDLE_ERROR == h_context) {
	printf ("failed to create gpudecryptor context!\n");
	return -1;
    }
    
    if (!test_batch_decrypt_cpu(h_context, N)) {
	return -1;
    }

    if (!test_batch_decrypt_gpu(h_context, N)) {
	return -1;
    }

    printf ("all tests passed.\n");

    mpz_clears(p, q, p1, q1, N, NULL);
    if (HANDLE_ERROR != h_context) {
	gpudecryptor_delete(h_context);
    }

    assert (close_native_library());

    printf ("goodbye.\n");

    return 0;
}
