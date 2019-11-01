#include "smallntt.h"
using namespace std;
using namespace seal;

void smallntt(Plaintext &plain, 
              util::SmallNTTTables* tables, 
              size_t dim,
              shared_ptr<const SEALContext::ContextData> context_data_)
{

    auto pool = MemoryManager::GetPool();
    auto &context_data = *context_data_;

    // Verify parameters.
    /*    
    if (!plain.is_valid_for(context_)){
      throw invalid_argument("plain is not valid for encryption parameters");
    }
    */

    if (plain.is_ntt_form()){
        throw invalid_argument("plain is already in NTT form");
    }
    if (!pool)
    {
        throw invalid_argument("pool is uninitialized");
    }

    // Extract encryption parameters.
    auto &parms = context_data.parms();
    auto parms_id = parms.parms_id();
    auto &coeff_modulus = parms.coeff_modulus();
    size_t coeff_count = parms.poly_modulus_degree();
    size_t coeff_mod_count = coeff_modulus.size();

    // if unit64_t a = t - x, then q - x = q - (t - (t - x)) = q - (t - a) = a + (q - t) 
    auto plain_upper_half_threshold = context_data.plain_upper_half_threshold(); // t/2
    auto plain_upper_half_increment = context_data.plain_upper_half_increment(); // q - t

    // Size check
    if (!util::product_fits_in(coeff_count, coeff_mod_count)){
        throw logic_error("invalid parameters");
    }

    // resize to fit the entire NTT transformed (ciphertext size) polynomial;
    // note that the new coefficients are automatically set to 0;
    // basically, this function makes coeff_mod_count many copies of the input 
    // plaintext --- one for each prime divisor of the current ciphertext modulus ---
    // so that, in the end, each copy holds the nnt domain representation of the 
    // input plaintext (extended periodically, as needed) w.r.t. its corresponding prime;
    plain.resize(coeff_count * coeff_mod_count);

    // Verify if plain lift is needed
    if (!context_data.qualifiers().using_fast_plain_lift){

      auto adjusted_poly(util::allocate_zero_uint(coeff_count * coeff_mod_count, pool));

      for (size_t i = 0; i < dim; i++){
        if (plain[i] >= plain_upper_half_threshold){
          util::add_uint_uint64(plain_upper_half_increment, plain[i],
                                coeff_mod_count, 
                                adjusted_poly.get() + (i * coeff_mod_count));
        } else {
          adjusted_poly[i * coeff_mod_count] = plain[i];
        }
      }
      decompose(context_data, adjusted_poly.get(), plain.data(), pool);
    }
    // No need for composed plain lift and decomposition
    else
    {
      for (size_t j = coeff_mod_count; j--; ){
        const uint64_t *plain_ptr = plain.data();
        uint64_t *adjusted_poly_ptr = plain.data() + (j * coeff_count);
        uint64_t current_plain_upper_half_increment = plain_upper_half_increment[j];
        for (size_t i = 0; i < dim; i++, plain_ptr++, adjusted_poly_ptr++){
          // Need to lift the coefficient in each qi
          if (*plain_ptr >= plain_upper_half_threshold){
            *adjusted_poly_ptr = *plain_ptr + current_plain_upper_half_increment;
          }
          // No need for lifting
          else
          {
          *adjusted_poly_ptr = *plain_ptr;
          }
        }
      }
    }

    /*
    size_t logd=util::get_power_of_two(dim);
    size_t logc=util::get_power_of_two(coeff_count);
    size_t dim_m1=dim-1;
    */

    // rat = 8192 / sellen = 8192 / (8192 / numsel) = numsel
    size_t rat = coeff_count / dim;

    // Transform to NTT domain
    // for each prime in ciphertext modulus
    for (size_t i = 0; i < coeff_mod_count; i++){

      auto dataloc = plain.data() + (i * coeff_count);

      ntt_negacyclic_harvey(dataloc , tables[i]);

      // save off one period's worth of data
      uint64_t data[dim];

      for(size_t j = 0; j < dim; j++){
        data[j] = *(dataloc + j);
      }
      /*
      for(size_t j=0;j<coeff_count;j++){
        *(dataloc+util::reverse_bits(j,logc))=data[j&dim_m1];	
      }
      */
		
      // extend periodically across all ntt slots
      for(size_t j=0; j < dim; j++){

	auto cploc = dataloc + j*rat;

	for (size_t k = 0; k < rat; k++){
	  *(cploc+k) = data[j];	
	}

      }

    }

    plain.parms_id() = parms_id;
}

void decompose(const SEALContext::ContextData &context_data, 
   const std::uint64_t *value, std::uint64_t *destination, util::MemoryPool &pool)
{
    auto &parms = context_data.parms();
    auto &coeff_modulus = parms.coeff_modulus();
    std::size_t coeff_count = parms.poly_modulus_degree();
    std::size_t coeff_mod_count = coeff_modulus.size();
    std::size_t rns_poly_uint64_count = 
        util::mul_safe(coeff_mod_count, coeff_count);
   if (coeff_mod_count == 1)
    {
        util::set_uint_uint(value, rns_poly_uint64_count, destination);
        return;
    }

    auto value_copy(util::allocate_uint(coeff_mod_count, pool));
    for (size_t i = 0; i < coeff_count; i++)
    {
        for (size_t j = 0; j < coeff_mod_count; j++)
        {
            //destination[i + (j * coeff_count)] = 
            //    util::modulo_uint(value + (i * coeff_mod_count),
            //        coeff_mod_count, coeff_modulus_[j], pool);

            // Manually inlined for efficiency
            // Make a fresh copy of value + (i * coeff_mod_count)
	
            util::set_uint_uint(
                value + (i * coeff_mod_count), coeff_mod_count, value_copy.get());

            // Starting from the top, reduce always 128-bit blocks
            for (std::size_t k = coeff_mod_count - 1; k--; )
            {
                value_copy[k] = util::barrett_reduce_128(
                    value_copy.get() + k, coeff_modulus[j]);
            }
            destination[i + (j * coeff_count)] = value_copy[0];
        }
    }
}


void tablegen(util::SmallNTTTables* outtables,shared_ptr<const SEALContext::ContextData> context_data
		,size_t logdim)
{
	size_t dim= 1<<logdim;
	auto parms=context_data->parms();
	auto coeff_modulus=parms.coeff_modulus();
	uint64_t coeff_num=coeff_modulus.size();
	size_t coeff_count=parms.poly_modulus_degree();
	size_t multiple=coeff_count/dim;
	size_t lgcc=util::get_power_of_two(coeff_count);
	size_t targetloc=util::reverse_bits(multiple,lgcc);
	for(size_t i=0;i<coeff_num;i++)
	{
		uint64_t root=context_data->small_ntt_tables()[i].get_from_root_powers(targetloc);
		auto modulus=coeff_modulus[i];
		outtables[i].generate(logdim,modulus,root);
	}
}
