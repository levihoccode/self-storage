{
  description = "Self-storage backend development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };
        in {
          default = pkgs.mkShell {
            packages = [
              pkgs.temurin-bin-17
              pkgs.maven
            ];

            shellHook = ''
              echo "Backend dev shell: Java 17 + Maven"
              java -version
              mvn -version
            '';
          };
        });
    };
}
