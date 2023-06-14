package gateway.service;

import gateway.model.AppUser;
import gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    @Autowired
    UserRepository repoUsr;

    @Override
    public UserDetails loadUserByUsername(String useremail) throws UsernameNotFoundException {

        AppUser appUser = repoUsr.getUserByEmail(useremail);

        if (appUser == null) {
            throw new UsernameNotFoundException(useremail);
        }
        UserDetails usr = User.withUsername(appUser.getEmail()).password(appUser.getPassword()).authorities(appUser.getRole()).build();
        return usr;

    }
}