{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };

          libs = with pkgs; [
            libpulseaudio
            libGL
            glfw
            openal
            flite
            stdenv.cc.cc.lib
          ];

          java = pkgs.jdk25;
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              java
              git
            ];

            buildInputs = libs;

            JAVA_HOME = java.home;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath libs;
          };
        });
    };
}
